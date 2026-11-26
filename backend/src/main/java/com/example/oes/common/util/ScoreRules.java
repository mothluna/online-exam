package com.example.oes.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 判分规则。纯函数、无状态、只做整数运算 —— 这样才能把边界用例全部表驱动覆盖。
 *
 * <p>分值单位统一为 0.01 分（int）。全流程不出现 float/double，
 * 除法只用于"少选按比例"和"按空平分"，一律向下取整，因此不存在 0.1+0.2 之类的误差。
 */
public final class ScoreRules {

    private ScoreRules() {
    }

    /**
     * 单选 / 判断：答案集合完全相同才得分。
     */
    public static int singleChoice(int fullScore, Set<String> correct, Set<String> user) {
        if (fullScore <= 0 || user == null || user.isEmpty() || correct == null || correct.isEmpty()) {
            return 0;
        }
        return correct.equals(user) ? fullScore : 0;
    }

    /**
     * 多选：全对满分；少选按命中比例得分（向下取整）；含错选或未作答得 0。
     * 用位掩码实现，避免集合交并差的开销，也避免顺序影响结果。
     */
    public static int multipleChoice(int fullScore, Set<String> correct, Set<String> user) {
        if (fullScore <= 0 || user == null || user.isEmpty() || correct == null || correct.isEmpty()) {
            return 0;
        }
        int correctMask = toMask(correct);
        int userMask = toMask(user);
        if ((userMask & ~correctMask) != 0) {
            // 选了不该选的，直接 0 分
            return 0;
        }
        if (userMask == correctMask) {
            return fullScore;
        }
        int hit = Integer.bitCount(userMask);
        int total = Integer.bitCount(correctMask);
        return fullScore * hit / total;
    }

    /**
     * 填空：按命中空数占比例计分。
     * 用 full * hits / n 而不是"每空平分 + 末空吃余数"——后者会让得分取决于
     * 命中第几空（200 分 3 空时，命中第 3 空得 68，命中第 1 空只有 66），不公平。
     */
    public static int blank(int fullScore, List<Set<String>> acceptedPerBlank, List<String> userAnswers) {
        if (fullScore <= 0 || acceptedPerBlank == null || acceptedPerBlank.isEmpty()) {
            return 0;
        }
        int blanks = acceptedPerBlank.size();
        int hits = 0;
        for (int i = 0; i < blanks; i++) {
            String user = userAnswers != null && i < userAnswers.size() ? userAnswers.get(i) : null;
            if (matches(user, acceptedPerBlank.get(i))) {
                hits++;
            }
        }
        if (hits == 0) {
            return 0;
        }
        if (hits == blanks) {
            // 全对直接返回满分，避免任何取整带来的"差 1 分"
            return fullScore;
        }
        return clamp(fullScore * hits / blanks, fullScore);
    }

    /**
     * 简答：关键词命中占比 × 满分，末位不设余数分配，保证"命中数相同则得分相同"。
     * 字数不足门槛直接 0 分；结果标记待复核，规则分不构成最终成绩。
     */
    public static int shortAnswer(int fullScore, List<String> keywords, String text, int minLength) {
        if (fullScore <= 0 || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String content = text == null ? "" : text.trim();
        if (content.length() < minLength) {
            return 0;
        }
        List<String> hits = hitKeywords(keywords, content);
        if (hits.isEmpty()) {
            return 0;
        }
        if (hits.size() == keywords.size()) {
            return fullScore;
        }
        return clamp(fullScore * hits.size() / keywords.size(), fullScore);
    }

    /** 命中的关键词，用于 judge_detail 留痕 */
    public static List<String> hitKeywords(List<String> keywords, String text) {
        List<String> hits = new ArrayList<>();
        if (keywords == null || text == null) {
            return hits;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()
                    && lower.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                hits.add(keyword.trim());
            }
        }
        return hits;
    }

    public static int clamp(int score, int fullScore) {
        if (score < 0) {
            return 0;
        }
        return Math.min(score, fullScore);
    }

    private static int toMask(Set<String> keys) {
        int mask = 0;
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            char c = key.trim().toUpperCase(Locale.ROOT).charAt(0);
            if (c >= 'A' && c <= 'Z') {
                mask |= 1 << (c - 'A');
            }
        }
        return mask;
    }

    private static boolean matches(String user, Set<String> accepted) {
        if (user == null || accepted == null || accepted.isEmpty()) {
            return false;
        }
        String normalized = normalizeBlank(user);
        for (String candidate : accepted) {
            if (candidate != null && normalizeBlank(candidate).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /** 去空白、转小写、全角转半角，避免"Object "与"object"被判错 */
    private static String normalizeBlank(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (Character.isWhitespace(c) || c == '\u3000') {
                continue;
            }
            if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
