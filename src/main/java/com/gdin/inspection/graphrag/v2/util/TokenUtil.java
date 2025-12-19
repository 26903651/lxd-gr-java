package com.gdin.inspection.graphrag.v2.util;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;

@Component
public class TokenUtil {
    @Resource
    private ResourceLoader resourceLoader;

    private Encoding encoding;

    @PostConstruct
    private void init() {
        EncodingRegistry registry = Encodings.newLazyEncodingRegistry();
        encoding = registry.getEncodingForModel("gpt-4").get();
    }

    public int getTokenCount(String text) {
        return encoding.countTokens(text);
    }
}
