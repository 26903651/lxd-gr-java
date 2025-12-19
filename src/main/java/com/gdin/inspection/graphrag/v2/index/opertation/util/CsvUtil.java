package com.gdin.inspection.graphrag.v2.index.opertation.util;

import java.util.*;
import java.util.stream.Collectors;

public final class CsvUtil {
    private CsvUtil() {}

    public static String toCsv(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return "";

        // pandas 默认列：以第一行 keys 顺序为主（dict 插入顺序）
        List<String> headers = new ArrayList<>(rows.get(0).keySet());

        String headerLine = String.join(",", headers);

        List<String> lines = new ArrayList<>();
        lines.add(headerLine);
        for (Map<String, Object> row : rows) {
            List<String> vals = headers.stream()
                    .map(h -> escapeCsv(valToString(row.get(h))))
                    .collect(Collectors.toList());
            lines.add(String.join(",", vals));
        }
        return String.join("\n", lines) + "\n";
    }

    private static String valToString(Object v) {
        if (v == null) return "";
        if (v instanceof Number) return String.valueOf(v);
        if (v instanceof Boolean) return ((Boolean) v) ? "True" : "False";
        return String.valueOf(v);
    }

    // 近似 pandas：包含逗号/引号/换行时加引号，引号翻倍
    private static String escapeCsv(String s) {
        if (s == null) return "";
        String t = s.replace("\r", " ").replace("\n", " ");
        boolean needQuote = t.contains(",") || t.contains("\"") || t.contains("\n");
        if (t.contains("\"")) t = t.replace("\"", "\"\"");
        return needQuote ? ("\"" + t + "\"") : t;
    }
}
