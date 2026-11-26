package com.example.oes.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 判分边界用例。全部断言使用整数（单位 0.01 分），
 * 一旦有人把分值改成 double，这些用例会立刻暴露精度问题。
 */
class ScoreRulesTest {

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    // ---------------- 多选 ----------------

    @ParameterizedTest(name = "多选 4 选 3，考生选 {0} → {1} 分")
    @CsvSource({
            "A B D, 300",   // 全对
            "A B,   200",   // 少选 2 个
            "A,     100",   // 少选 1 个
            "A B C,   0",   // 含错选
            "A C,     0",   // 少选但含错选
    })
    @DisplayName("多选：全对满分、少选按比例、错选零分")
    void multipleChoice(String user, int expected) {
        assertEquals(expected, ScoreRules.multipleChoice(300, set("A", "B", "D"), set(user.split(" "))));
    }

    @Test
    @DisplayName("多选：未作答得 0")
    void multipleChoiceBlank() {
        assertEquals(0, ScoreRules.multipleChoice(300, set("A", "B"), set()));
    }

    @Test
    @DisplayName("多选：2 选 1 时向下取整，不出现 0.5 分")
    void multipleChoiceHalfRoundsDown() {
        // 分值 3.00 分（300），2 个正确答案选 1 个 → 150
        assertEquals(150, ScoreRules.multipleChoice(300, set("A", "C"), set("A")));
        // 奇数分值 3.01 分，2 选 1 → 301/2 = 150（向下取整）
        assertEquals(150, ScoreRules.multipleChoice(301, set("A", "C"), set("A")));
    }

    // ---------------- 单选 / 判断 ----------------

    @Test
    @DisplayName("单选：半分的题精确得到 50，不会出现 49.99")
    void singleChoiceHalfScore() {
        assertEquals(50, ScoreRules.singleChoice(50, set("B"), set("B")));
    }

    @Test
    @DisplayName("单选：答错得 0")
    void singleChoiceWrong() {
        assertEquals(0, ScoreRules.singleChoice(50, set("B"), set("A")));
    }

    @Test
    @DisplayName("判断：多选了别的键视为错误")
    void judgeChoiceExtra() {
        assertEquals(0, ScoreRules.singleChoice(100, set("正确"), set("正确", "错误")));
    }

    // ---------------- 填空 ----------------

    @Test
    @DisplayName("填空：3 空全对时合计精确等于满分")
    void blankAllCorrect() {
        List<Set<String>> accepted = List.of(set("Object"), set("java.lang"), set("垃圾回收"));
        assertEquals(200, ScoreRules.blank(200, accepted, List.of("Object", "java.lang", "垃圾回收")));
    }

    @Test
    @DisplayName("填空：200 分 3 空，答对两空得 133 分（200×2/3）")
    void blankPartial() {
        List<Set<String>> accepted = List.of(set("A"), set("B"), set("C"));
        assertEquals(133, ScoreRules.blank(200, accepted, List.of("A", "B", "随便写")));
    }

    @Test
    @DisplayName("填空：得分与命中第几空无关（对称性）")
    void blankSymmetric() {
        List<Set<String>> accepted = List.of(set("A"), set("B"), set("C"));
        int hitFirst = ScoreRules.blank(200, accepted, List.of("A", "x", "x"));
        int hitLast = ScoreRules.blank(200, accepted, List.of("x", "x", "C"));
        assertEquals(hitFirst, hitLast);
        assertEquals(66, hitFirst);
    }

    @Test
    @DisplayName("填空：忽略大小写、首尾空白与全角字符")
    void blankNormalize() {
        List<Set<String>> accepted = List.of(set("object"), set("JAVA"));
        assertEquals(200, ScoreRules.blank(200, accepted, List.of("  Object ", "ｊａｖａ")));
    }

    @Test
    @DisplayName("填空：多答案用 | 分隔，命中任一即算对")
    void blankMultipleAccepted() {
        List<Set<String>> accepted = List.of(new LinkedHashSet<>(List.of("Object", "java.lang.Object")));
        assertEquals(100, ScoreRules.blank(100, accepted, List.of("java.lang.Object")));
    }

    // ---------------- 简答 ----------------

    @Test
    @DisplayName("简答：命中全部关键词得满分")
    void shortAllKeywords() {
        List<String> keywords = List.of("可达性分析", "分代", "回收");
        assertEquals(1000, ScoreRules.shortAnswer(1000,
                keywords, "通过可达性分析判断对象存活，再按分代策略进行回收。", 10));
    }

    @Test
    @DisplayName("简答：命中 1/3 关键词得 333，命中 2/3 得 666")
    void shortPartial() {
        List<String> keywords = List.of("可达性分析", "分代", "回收");
        assertEquals(333, ScoreRules.shortAnswer(1000,
                keywords, "通过可达性分析判断对象是否存活，这是核心思路。", 10));
        assertEquals(666, ScoreRules.shortAnswer(1000,
                keywords, "通过可达性分析判断存活，并按分代策略处理。", 10));
    }

    @Test
    @DisplayName("简答：字数不足门槛直接 0 分")
    void shortTooShort() {
        List<String> keywords = List.of("可达性分析");
        assertEquals(0, ScoreRules.shortAnswer(1000, keywords, "可达性", 10));
    }

    @Test
    @DisplayName("简答：未作答得 0")
    void shortEmpty() {
        assertEquals(0, ScoreRules.shortAnswer(1000, List.of("a"), "", 10));
        assertEquals(0, ScoreRules.shortAnswer(1000, List.of("a"), null, 10));
    }

    @Test
    @DisplayName("简答：命中判定大小写不敏感")
    void shortCaseInsensitive() {
        assertEquals(100, ScoreRules.shortAnswer(100, List.of("JVM"), "jvm 是虚拟机，负责执行字节码。", 5));
    }

    // ---------------- 全局约束 ----------------

    @Test
    @DisplayName("100 题 × 0.33 分，全对合计 3300，无累计误差")
    void noAccumulatedError() {
        int total = 0;
        for (int i = 0; i < 100; i++) {
            total += ScoreRules.singleChoice(33, set("A"), set("A"));
        }
        assertEquals(3300, total);
    }

    @Test
    @DisplayName("任何规则下得分都落在 [0, 满分] 区间")
    void alwaysWithinRange() {
        assertEquals(0, ScoreRules.clamp(-10, 100));
        assertEquals(100, ScoreRules.clamp(120, 100));
        assertEquals(0, ScoreRules.singleChoice(0, set("A"), set("A")));
        assertEquals(0, ScoreRules.multipleChoice(0, set("A"), set("A")));
        assertEquals(0, ScoreRules.blank(0, List.of(set("A")), List.of("A")));
    }
}
