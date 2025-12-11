package com.gdin.inspection.graphrag.util;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.gdin.inspection.graphrag.resp.BaseSseResp;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Slf4j
public class SseUtil {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Set<SseEmitter> sseEmitterSet = ConcurrentHashMap.newKeySet();

    static{
        Runnable task = () -> {
            sseEmitterSet.stream().toList().forEach(emitter -> {
                try {
                    send(emitter, BaseSseResp.builder().text("").state(BaseSseResp.STATE_HEARTBEAT).build());
                } catch (IOException ignored) {}
            });
        };
        scheduler.scheduleAtFixedRate(task, 0, 10, TimeUnit.SECONDS);
    }

    public static SseEmitter createCommonSseEmitter() {
        return createCommonSseEmitter(null);
    }

    public static SseEmitter createCommonSseEmitter(CountDownLatch countDownLatch) {
        return createSpecialSseEmitter(countDownLatch, null, null, null);
    }

    public static SseEmitter createSpecialSseEmitter(CountDownLatch countDownLatch, Runnable onCompletion, Consumer<Throwable> onError, Runnable onTimeout) {
        return createSseEmitterWithHeartbeat(
                () -> {
                    if(onCompletion!=null) onCompletion.run();
                    log.info("SSE connection completed");
                },
                (error) -> {
                    if(onError!=null) onError.accept(error);
                    if(countDownLatch!=null) countDownLatch.countDown();
                    if (error instanceof IOException) {
                        log.warn("SSE disconnected:{}", error.getMessage());
                    } else {
                        log.error("SSE connection error: {}", error.getMessage(), error);
                    }
                },
                () -> {
                    if(onTimeout!=null) onTimeout.run();
                    if(countDownLatch!=null) countDownLatch.countDown();
                    log.warn("SSE connection timed out");
                });
    }

    public static SseEmitter createSseEmitterWithHeartbeat(Runnable onCompletion, Consumer<Throwable> onError, Runnable onTimeout) {
        SseEmitter emitter = new SseEmitter(3600000L);
        sseEmitterSet.add(emitter);
        emitter.onCompletion(() -> {
            if(onCompletion!=null) emitter.onCompletion(onCompletion);
            sseEmitterSet.remove(emitter);
        });
        emitter.onError((error) -> {
            if(onError!=null) emitter.onError(onError);
            emitter.complete();
        });
        emitter.onTimeout(() -> {
            if(onTimeout!=null) emitter.onTimeout(onTimeout);
            emitter.complete();
        });
        return emitter;
    }

    public static void send(SseEmitter emitter, Object data) throws IOException {
        if (checkConnectAlive(emitter)) {
            emitter.send(SseEmitter.event()
                    .id(IdUtil.getSnowflakeNextIdStr())
                    .name(data.getClass().getSimpleName())
                    .data(IOUtil.jsonSerializeWithNoType(data))
                    .build()
            );
        }
    }

    public static String removeThink(String response){
        // 去掉<think></think>标签以及其中的所有内容
        return Pattern.compile("<think>.*?</think>", Pattern.DOTALL).matcher(response).replaceAll("");
    }

    public static String getResponseWithoutThink(SseEmitter sseEmitter, TokenStream tokenStream, String id) throws InterruptedException {
        String result = getResponse(sseEmitter, tokenStream, id);
        return removeThink(result);
    }

    public static String getResponse(SseEmitter sseEmitter, TokenStream tokenStream, String id) throws InterruptedException {
        return getResponse(sseEmitter, tokenStream, id, new CountDownLatch(1));
    }

    public static String getResponse(SseEmitter sseEmitter, TokenStream tokenStream, String id, CountDownLatch latch) throws InterruptedException {
        return sendStream(sseEmitter, tokenStream, id, latch, false);
    }

    public static String sendStream(SseEmitter sseEmitter, TokenStream tokenStream, String id, CountDownLatch latch, boolean closeSse) throws InterruptedException {
        StringBuilder result = new StringBuilder();
        log.info("开始进入大模型流式响应");
        long startTime = System.currentTimeMillis();
        tokenStream.onPartialResponse(s -> {
                    if (result.length() == 0) {
                        log.info("获取到大模型回答内容");
                        log.info("流式响应到获取内容响应耗时：{} ms", System.currentTimeMillis() - startTime);
                    }
                    try {
//                        log.info(s);
                        result.append(s);
                        if(sseEmitter!=null) SseUtil.send(sseEmitter, BaseSseResp.builder()
                                .id(id)
                                .state(BaseSseResp.STATE_ASSISTANT_GEN)
                                .text(s)
                                .build());
                    } catch (IOException e) {
                        // 如果客户端断开连接，不再发送
                        log.warn("sse断开连接：{}", e.getMessage());
                        if(sseEmitter!=null) sseEmitter.complete();
                    }
                })
                .onCompleteResponse(chatResponse -> {
                    if(latch!=null) latch.countDown();
                    if(closeSse&&sseEmitter!=null) sseEmitter.complete();
                    log.info("SSE [{}] 返回内容: {}", id, result);
                    log.info("SSE [{}] 耗时: {}ms", id, System.currentTimeMillis() - startTime);
                })
                .onError(throwable -> {
                    log.error("throwable:{}", throwable);
                    result.setLength(0);
                    try {
                        if(sseEmitter!=null) SseUtil.send(sseEmitter, BaseSseResp.builder()
                                .id(id)
                                .state(BaseSseResp.STATE_ERROR)
                                .text(throwable.getMessage())
                                .build());
                    } catch (IOException e) {
                        log.warn("sse断开连接：{}", e.getMessage());
                        if(sseEmitter!=null) sseEmitter.complete();
                    }
                    if(latch!=null) latch.countDown();
                    if(closeSse&&sseEmitter!=null) sseEmitter.completeWithError(throwable);
                })
                .start();
        if(latch!=null) latch.await(sseEmitter==null?1800000L:sseEmitter.getTimeout(), TimeUnit.MILLISECONDS);
        if(latch!=null&&result.isEmpty()) throw new RuntimeException("调用失败, 返回为空");
        return result.toString();
    }

    public static JSONObject getJSONResponse(SseEmitter sseEmitter, TokenStream tokenStream, String id) throws InterruptedException {
        String result = getResponse(sseEmitter, tokenStream, id);
        // System.out.println("result: " + result);
        return getJSONResponse(result);
    }

    public static JSONObject getJSONResponse(String response){
        // 去掉think的内容
        String answer = removeThink(response);
        // log.info("answer: {}", answer);
        // 获取文本中的json部分, 即{}中的内容
        if(answer.indexOf('{')==-1) answer = "{" + answer + "}";
        answer = answer.substring(answer.indexOf("{"), answer.lastIndexOf("}") + 1);
        return JSONObject.parseObject(answer);
    }

    public static JSONArray getJSONArrayResponse(SseEmitter sseEmitter, TokenStream tokenStream, String id) throws InterruptedException {
        String result = getResponse(sseEmitter, tokenStream, id);
        // System.out.println("result: " + result);
        return getJSONArrayResponse(result);
    }

    public static JSONArray getJSONArrayResponse(String response){
        // 去掉think的内容
        String answer = removeThink(response);
        // log.info("answer: {}", answer);
        // 获取文本中的json部分, 即[]中的内容
        if(answer.indexOf('[')==-1) answer = "[" + answer + "]";
        answer = answer.substring(answer.indexOf("["), answer.lastIndexOf("]") + 1);
        return JSONArray.parseArray(answer);
    }

    /**
     * 判断sse连接是否可用 (未完成并且没有错误)
     * @param emitter
     * @return
     */
    public static boolean checkConnectAlive(SseEmitter emitter) {
        if (emitter == null) return false;
        return !isEmitterComplete(emitter) &&
                !isEmitterError(emitter);
    }

    public static boolean isEmitterComplete(SseEmitter emitter) {
        try {
            Field completeField = ResponseBodyEmitter.class.getDeclaredField("complete");
            completeField.setAccessible(true);
            return (boolean) completeField.get(emitter);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEmitterError(SseEmitter emitter) {
        try {
            Field errorOnSendField = ResponseBodyEmitter.class.getDeclaredField("ioErrorOnSend");
            errorOnSendField.setAccessible(true);
            return (boolean) errorOnSendField.get(emitter);
        } catch (Exception e) {
            return false;
        }
    }
}
