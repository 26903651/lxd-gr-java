package com.gdin.inspection.graphrag.v2.util;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

public final class PyStrUtil {

    // 等价于 Python: r"[\x00-\x1f\x7f-\x9f]"
    private static final Pattern CTRL = Pattern.compile("[\\x00-\\x1F\\x7F-\\x9F]");

    private PyStrUtil() {}

    public static String cleanStr(Object input) {
        if (!(input instanceof String)) {
            // Python: 非 str 直接原样返回。Java 这里我们只能转成字符串或返回 null。
            return input == null ? null : String.valueOf(input);
        }
        String s = ((String) input).trim();
        String unescaped = HtmlUtils.htmlUnescape(s);
        return CTRL.matcher(unescaped).replaceAll("");
    }
}
