package com.gdin.inspection.graphrag.v2.util;

import java.util.*;
import java.util.stream.Collectors;

public final class CsvUtil {
    private CsvUtil() {}

    /** 兼容旧逻辑：逗号分隔，近似 pandas */
    public static String toCsv(List<Map<String, Object>> rows) {
        return toCsv(rows, ",", '"', false, null);
    }

    /**
     * 新增：可指定 delimiter 与 escapechar，用于对齐 graphrag basic_search 的 DataFrame.to_csv(index=False, escapechar="\\", sep="|")
     *
     * @param rows 行数据
     * @param delimiter 分隔符，basic search 用 "|"
     * @param escapeChar 转义字符，basic search 用 '\\'
     * @param replaceNewlines 是否将 \r \n 替换为空格（你旧实现是替换；pandas 不一定替换，但替换更安全）
     * @param headers 指定列顺序；为 null 时沿用第一行 key 顺序
     */
    public static String toCsv(
            List<Map<String, Object>> rows,
            String delimiter,
            char escapeChar,
            boolean replaceNewlines,
            List<String> headers
    ) {
        if (rows == null || rows.isEmpty()) return "";

        List<String> cols = (headers != null && !headers.isEmpty())
                ? new ArrayList<>(headers)
                : new ArrayList<>(rows.get(0).keySet());

        List<String> lines = new ArrayList<>();
        lines.add(String.join(delimiter, cols));

        for (Map<String, Object> row : rows) {
            List<String> vals = cols.stream()
                    .map(h -> escapeField(valToString(row.get(h)), delimiter, escapeChar, replaceNewlines))
                    .collect(Collectors.toList());
            lines.add(String.join(delimiter, vals));
        }
        return String.join("\n", lines) + "\n";
    }

    private static String valToString(Object v) {
        if (v == null) return "";
        if (v instanceof Number) return String.valueOf(v);
        if (v instanceof Boolean) return ((Boolean) v) ? "True" : "False";
        return String.valueOf(v);
    }

    /**
     * 对齐目标：pandas + escapechar
     * - 含 delimiter / quote / 换行 => 用 quote 包裹
     * - 在 quote 包裹内部：对 quote、delimiter、换行做 escapeChar 前缀转义
     */
    private static String escapeField(String s, String delimiter, char escapeChar, boolean replaceNewlines) {
        if (s == null) return "";
        String t = s;
        if (replaceNewlines) {
            t = t.replace("\r", " ").replace("\n", " ");
        }

        boolean needQuote = t.contains(delimiter) || t.contains("\"") || t.contains("\n") || t.contains("\r");
        if (!needQuote) return t;

        StringBuilder out = new StringBuilder(t.length() + 8);
        out.append('"');
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"' || c == '\n' || c == '\r') {
                out.append(escapeChar);
                out.append(c);
            } else {
                // delimiter 可能是多字符，这里只对单字符 delimiter 做逐字符 escape；
                // basic search delimiter 固定为 "|"，单字符，严格对齐足够
                if (delimiter.length() == 1 && c == delimiter.charAt(0)) {
                    out.append(escapeChar);
                }
                out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }
}
