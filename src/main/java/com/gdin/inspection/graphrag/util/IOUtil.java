package com.gdin.inspection.graphrag.util;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Slf4j
public class IOUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ObjectMapper simpleMapper = new ObjectMapper();

    static {
        // 配置 ObjectMapper 以包含类型信息
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        objectMapper.registerModule(new JavaTimeModule());
        // 避免写成时间戳（否则 Instant 会变成 long）
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        simpleMapper.registerModule(new JavaTimeModule());
        // 避免写成时间戳（否则 Instant 会变成 long）
        simpleMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static String jsonSerialize(Object obj) throws JsonProcessingException {
        return jsonSerialize(obj, false);
    }

    public static String jsonSerialize(Object obj, boolean pretty) throws JsonProcessingException {
        if(obj==null) return null;
        if(pretty) return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        else return objectMapper.writeValueAsString(obj);
    }

    /**
     * json序列化(不降类型信息序列化到json字符串中)
     */
    public static String jsonSerializeWithNoType(Object obj) throws JsonProcessingException {
        return jsonSerializeWithNoType(obj, false);
    }

    /**
     * json序列化(不降类型信息序列化到json字符串中)
     */
    public static String jsonSerializeWithNoType(Object obj, boolean pretty) throws JsonProcessingException {
        if (obj == null) {
            return null;
        } else {
            return pretty ? simpleMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj) : simpleMapper.writeValueAsString(obj);
        }
    }

    /**
     * json反序列化(json字符串中不包含类型信息)
     */
    public static Object jsonDeserializeWithNoType(InputStream is) throws IOException {
        return simpleMapper.readValue(is, Object.class);
    }

    /**
     * json反序列化(json字符串中不包含类型信息)
     */
    public static <T> T jsonDeserializeWithNoType(InputStream is, Class<T> clazz) throws IOException {
        return simpleMapper.readValue(is, clazz);
    }

    /**
     * json反序列化(json字符串中不包含类型信息)
     */
    public static <T> T jsonDeserializeWithNoType(String content, Class<T> clazz) throws IOException {
        return simpleMapper.readValue(content, clazz);
    }

    public static Object jsonDeserialize(String serializedJson) throws JsonProcessingException {
        return serializedJson == null ? null : objectMapper.readValue(serializedJson, Object.class);
    }

    public static <T> T jsonDeserialize(String serializedJson, Class<T> clazz) throws JsonProcessingException {
        return serializedJson == null ? null : objectMapper.readValue(serializedJson, clazz);
    }

    /**
     * 将JSON字符串解析为指定类型的列表
     * @param json JSON字符串
     * @param elementType 列表元素类型
     * @param <T> 返回列表元素类型
     * @return 解析后的对象列表，解析失败返回空列表
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> parseList(String json, Class<? extends T> elementType) {
        try {
            if (Strings.isBlank(json)) {
                return Collections.emptyList();
            }
            JavaType type = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, elementType);
            return (List<T>) objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("JSON解析失败: {}", json, e);
            return Collections.emptyList();
        }
    }

}
