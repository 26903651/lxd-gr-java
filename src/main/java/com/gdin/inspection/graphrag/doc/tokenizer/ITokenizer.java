package com.gdin.inspection.graphrag.doc.tokenizer;

import com.gdin.inspection.graphrag.pojo.Token;

import java.util.List;

public interface ITokenizer {

    List<Token> parse(String text) throws Exception;
}
