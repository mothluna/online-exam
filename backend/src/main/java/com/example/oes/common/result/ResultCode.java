package com.example.oes.common.result;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码。前三位与 HTTP 状态码对齐，便于排障与前端分流处理。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok", HttpStatus.OK),

    PARAM_INVALID(40000, "参数校验失败", HttpStatus.BAD_REQUEST),

    NOT_FOUND(40400, "请求的资源不存在", HttpStatus.NOT_FOUND),

    UNAUTHORIZED(40100, "未认证，请先登录", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(40101, "令牌无效或已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED(40102, "令牌已失效，请重新登录", HttpStatus.UNAUTHORIZED),

    FORBIDDEN_ROLE(40300, "无权限访问该接口", HttpStatus.FORBIDDEN),
    FORBIDDEN_RESOURCE(40301, "无权访问该资源", HttpStatus.FORBIDDEN),

    ALREADY_STARTED(40901, "已开考，请继续作答", HttpStatus.CONFLICT),
    ALREADY_SUBMITTED(40902, "已交卷，不可重复提交", HttpStatus.CONFLICT),
    DUPLICATE_STEM(40903, "题干重复", HttpStatus.CONFLICT),

    PAPER_SCORE_MISMATCH(42201, "试卷总分与规则不一致", HttpStatus.UNPROCESSABLE_ENTITY),
    POOL_INSUFFICIENT(42202, "题池数量不足", HttpStatus.UNPROCESSABLE_ENTITY),

    TOO_MANY_REQUESTS(42901, "请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),

    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
