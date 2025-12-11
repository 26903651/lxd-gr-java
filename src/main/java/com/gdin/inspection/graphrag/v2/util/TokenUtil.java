package com.gdin.inspection.graphrag.v2.util;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import jakarta.annotation.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class TokenUtil {
    @Resource
    private ResourceLoader resourceLoader;

    public int getTokenCount(String text) throws IOException {
        File file = resourceLoader.getResource("classpath:models/qwen3/tokenizer.json").getFile();
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(file.toPath());
        return tokenizer.encode(text).getTokens().length;
    }
}
