package com.example.oes.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 题干归一化与指纹。
 *
 * <p>目的：让"下列哪个是…？"与"下列哪个是...?"被判为同一题干，
 * 而"1+1=2"与"1+1=3"不会误判。归一化规则一旦上线就不能随意改，
 * 否则历史 stem_hash 全部失效，需要在发版说明中标注并重算。
 */
public final class StemNormalizer {

    private static final char[] PUNCT = {
            ',', '.', ';', ':', '!', '?', '(', ')', '[', ']', '<', '>',
            '"', '\'', '、', '。', '，', '；', '：', '！', '？', '（', '）',
            '【', '】', '《', '》', '“', '”', '‘', '’'
    };

    private StemNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace('\u00A0', ' ').replace('\u3000', ' ');

        StringBuilder sb = new StringBuilder(s.length());
        boolean lastSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                // 连续空白压缩为单个半角空格
                if (!lastSpace && sb.length() > 0) {
                    sb.append(' ');
                    lastSpace = true;
                }
                continue;
            }
            lastSpace = false;
            sb.append(unify(c));
        }

        String compact = sb.toString().trim();
        int start = 0;
        int end = compact.length();
        while (start < end && isPunct(compact.charAt(start))) {
            start++;
        }
        while (end > start && isPunct(compact.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return "";
        }
        return compact.substring(start, end).toLowerCase(Locale.ROOT);
    }

    public static String hash(String raw) {
        String normalized = normalize(raw);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，走到这里说明运行环境被裁剪
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static char unify(char c) {
        // 全角 ASCII 区（！到 ～）转半角
        if (c >= '\uFF01' && c <= '\uFF5E') {
            return (char) (c - 0xFEE0);
        }
        return switch (c) {
            case '。' -> '.';
            case '，' -> ',';
            case '；' -> ';';
            case '：' -> ':';
            case '！' -> '!';
            case '？' -> '?';
            case '（' -> '(';
            case '）' -> ')';
            case '【', '《' -> '[';
            case '】', '》' -> ']';
            case '“', '”' -> '"';
            case '‘', '’' -> '\'';
            case '、' -> ',';
            default -> c;
        };
    }

    private static boolean isPunct(char c) {
        for (char p : PUNCT) {
            if (p == c) {
                return true;
            }
        }
        return false;
    }
}
