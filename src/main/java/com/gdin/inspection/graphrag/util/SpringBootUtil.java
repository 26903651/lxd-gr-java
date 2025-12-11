package com.gdin.inspection.graphrag.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SpringBootUtil implements ApplicationContextAware {
    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        SpringBootUtil.context = context;
    }

    public static ApplicationContext getApplicationContext() {
        return context;
    }

    public static Object getBean(String name) {
        return context.getBean(name);
    }

    public static <T> T getBean(String name, Class<T> clazz) {
        return (T) context.getBean(name);
    }

    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    public static Environment getEnvironment() {
        return context.getEnvironment();
    }

    public static ClassLoader getClassLoader() {
        return context.getClassLoader();
    }

    public static Map<String, Object> configPropsToMap(Object properties) {
        String prefix = null;
        ConfigurationProperties annotation = properties.getClass().getAnnotation(ConfigurationProperties.class);
        if(annotation!=null) prefix = annotation.prefix();
        return getPropertiesMap(properties, prefix);
    }

    private static String getPrefixWithPoint(String prefix){
        return prefix==null?"":prefix + ".";
    }

    private static Map<String, Object> getPropertiesMap(Object obj, String prefix) {
        Map<String, Object> result = new HashMap<>();
        Field[] fields = obj.getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                String name = field.getName();

                if (value != null) {
                    if (isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
                        result.put(getPrefixWithPoint(prefix) + convertToKebabCase(name), value);
                    } else if (value instanceof List) {
                        for (int i = 0; i < ((List<?>) value).size(); i++) {
                            result.put(getPrefixWithPoint(prefix) + convertToKebabCase(name) + "[" + i + "]", ((List<?>) value).get(i));
                        }
                    } else if (value instanceof Map) {
                        ((Map<?, ?>) value).forEach((key, mapValue) -> {
                            if (isPrimitiveOrWrapper(mapValue.getClass()) || mapValue instanceof String) result.put(getPrefixWithPoint(prefix) + convertToKebabCase(name) + "." + key, mapValue);
                            else result.putAll(getPropertiesMap(mapValue, getPrefixWithPoint(prefix) + convertToKebabCase(name) + "." + key));
                        });
                    } else {
                        result.putAll(getPropertiesMap(value, getPrefixWithPoint(prefix) + convertToKebabCase(name)));
                    }
                }

            } catch (IllegalAccessException e) {
                log.error(e.getMessage(), e);
            }
        }
        return result;
    }

    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
                type == Boolean.class || type == Byte.class || type == Character.class ||
                type == Double.class || type == Float.class || type == Integer.class ||
                type == Long.class || type == Short.class;
    }

    private static String convertToKebabCase(String str) {
        StringBuilder result = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append("-").append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
