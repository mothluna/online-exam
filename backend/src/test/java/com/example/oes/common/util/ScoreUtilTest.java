package com.example.oes.common.util;

import com.example.oes.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 分值换算：元与 0.01 分互转必须精确，这是"无浮点误差"的地基。 */
class ScoreUtilTest {

    @Test
    @DisplayName("两位小数精确换算")
    void toCent() {
        assertEquals(250, ScoreUtil.toCent(new BigDecimal("2.50")));
        assertEquals(1, ScoreUtil.toCent(new BigDecimal("0.01")));
        assertEquals(10000, ScoreUtil.toCent(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("超过两位小数直接拒绝，不做静默四舍五入")
    void toCentRejectsThreeDecimals() {
        assertThrows(BizException.class, () -> ScoreUtil.toCent(new BigDecimal("1.234")));
    }

    @Test
    @DisplayName("空值拒绝")
    void toCentRejectsNull() {
        assertThrows(BizException.class, () -> ScoreUtil.toCent(null));
    }

    @Test
    @DisplayName("整数分转元保留两位")
    void toYuan() {
        assertEquals(new BigDecimal("2.50"), ScoreUtil.toYuan(250));
        assertEquals(new BigDecimal("0.01"), ScoreUtil.toYuan(1));
        assertEquals(new BigDecimal("0.00"), ScoreUtil.toYuan(0));
    }

    @Test
    @DisplayName("0.1 + 0.2 精确等于 0.30（用整数运算绕开浮点）")
    void noFloatingPointError() {
        int cent = ScoreUtil.toCent(new BigDecimal("0.10")) + ScoreUtil.toCent(new BigDecimal("0.20"));
        assertEquals(new BigDecimal("0.30"), ScoreUtil.toYuan(cent));
    }
}
