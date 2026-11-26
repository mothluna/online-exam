package com.example.oes.common.model;

/**
 * 选项。接口入参、Excel 导入、持久化三层共用同一个结构，避免各写一份再互相转换。
 */
public record OptionItem(String key, String content) {
}
