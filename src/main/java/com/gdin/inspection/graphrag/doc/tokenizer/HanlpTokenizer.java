package com.gdin.inspection.graphrag.doc.tokenizer;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.gdin.inspection.graphrag.config.properties.DocProperties;
import com.gdin.inspection.graphrag.pojo.Token;
import com.gdin.inspection.graphrag.util.HttpClientUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class HanlpTokenizer implements ITokenizer {
    @Resource
    private DocProperties docProperties;

    @Override
    public List<Token> parse(String text) throws Exception {
        List<Token> tokenList = new ArrayList<>();
        DocProperties.HanlpTokenizerProperties hanlpTokenizer = docProperties.getHanlpTokenizer();
        try (CloseableHttpClient httpClient = HttpClientUtil.getApacheClient(hanlpTokenizer.getTimeoutInSeconds())) {
            // 2. 构建 POST 请求
            HttpPost post = new HttpPost(hanlpTokenizer.getBaseUrl()+"/parse");
            post.addHeader("Accept", "application/json");
            post.addHeader("Content-Type", "application/json");
            // 3. 构建 请求体
            JSONObject reqJson = new JSONObject();
            reqJson.put("text", text);
            if(hanlpTokenizer.getModel()!=null) reqJson.put("model", hanlpTokenizer.getModel());
            if(hanlpTokenizer.getPosModel()!=null) reqJson.put("pos_model", hanlpTokenizer.getPosModel());
            HttpEntity httpEntity = EntityBuilder.create()
                    .setText(reqJson.toJSONString())
                    .setContentType(ContentType.APPLICATION_JSON)
                    .build();
            post.setEntity(httpEntity);
            // 4. 执行请求并处理响应
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                String respBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                if(response.getCode()!=200) throw new RuntimeException("Hanlp 调用失败: " + respBody);
                log.info("返回解析数据如下: {}", respBody);
                JSONObject respJson = JSONObject.parseObject(respBody);
                JSONArray tokens = respJson.getJSONArray("tokens");
                for (int i = 0; i < tokens.size(); i++) {
                    JSONObject tokenObj = tokens.getJSONObject(i);
                    tokenList.add(Token.builder().word(tokenObj.getString("word")).pos(tokenObj.getString("pos")).build());
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
        return tokenList;
    }
}
