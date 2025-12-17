package com.gdin.inspection.graphrag.v2.index.update;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StringListJsonCodec {

    private StringListJsonCodec() {}

    public static String encode(List<String> list) {
        if (list == null) return "[]";
        return JSON.toJSONString(list);
    }

    public static List<String> decode(String s) {
        if (s == null || s.isBlank()) return Collections.emptyList();
        String t = s.trim();

        // 若不是 JSON 数组，按 Python list(str) 语义当成单条
        if (!t.startsWith("[") || !t.endsWith("]")) {
            List<String> one = new ArrayList<>(1);
            one.add(t);
            return one;
        }

        try {
            JSONArray arr = JSON.parseArray(t);
            List<String> out = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                Object v = arr.get(i);
                out.add(v == null ? "null" : String.valueOf(v));
            }
            return out;
        } catch (Exception e) {
            List<String> one = new ArrayList<>(1);
            one.add(t);
            return one;
        }
    }
}
