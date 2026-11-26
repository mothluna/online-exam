package com.example.oes.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 题干归一化是去重的唯一依据（见 D-007），规则一旦上线不能改，
 * 因此这里把每条规则都钉死成用例，改动函数体会立刻暴露。
 */
class StemNormalizerTest {

    @Test
    @DisplayName("全角问号与首尾空格不影响判定")
    void fullWidthAndSpaces() {
        String a = "下列哪个是接口关键字？";
        String b = "  下列哪个是接口关键字?  ";
        assertEquals(StemNormalizer.normalize(a), StemNormalizer.normalize(b));
        assertEquals(StemNormalizer.hash(a), StemNormalizer.hash(b));
    }

    @Test
    @DisplayName("连续空白（含制表符、换行、全角空格）压缩为一个空格")
    void collapseWhitespace() {
        assertEquals("a b c", StemNormalizer.normalize("a \t\n\u3000  b   c"));
    }

    @Test
    @DisplayName("英文转小写，中文不受影响")
    void lowerCaseAsciiOnly() {
        assertEquals(StemNormalizer.normalize("Java集合"), StemNormalizer.normalize("java集合"));
        assertEquals("java 集合", StemNormalizer.normalize("JAVA   集合"));
    }

    @Test
    @DisplayName("中英文标点统一")
    void unifyPunctuation() {
        // 中文标点转半角后不补空格；末尾标点会被"去首尾标点"规则去掉
        assertEquals("列表,集合;映射", StemNormalizer.normalize("列表，集合；映射。"));
        assertEquals("a, b; c", StemNormalizer.normalize("a, b; c."));
    }

    @Test
    @DisplayName("去首尾标点，但保留中间的")
    void trimEdgePunctuation() {
        assertEquals("abc", StemNormalizer.normalize("。abc！"));
    }

    @Test
    @DisplayName("归一化后为空串的输入：hash 依然是稳定值")
    void blankInput() {
        assertEquals("", StemNormalizer.normalize(null));
        assertEquals("", StemNormalizer.normalize("   "));
        assertEquals(StemNormalizer.hash(""), StemNormalizer.hash("。"));
    }

    @Test
    @DisplayName("内容不同的题干必须得到不同指纹")
    void differentStemsDifferentHash() {
        assertNotEquals(StemNormalizer.hash("1+1=2"), StemNormalizer.hash("1+1=3"));
    }

    @Test
    @DisplayName("SHA-256 输出为 64 位小写十六进制")
    void hashFormat() {
        String hash = StemNormalizer.hash("任意题干");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }
}
