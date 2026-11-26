package com.example.oes.common.exception;

import com.example.oes.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：可预期的失败（参数错误、无权访问、状态冲突等）。
 * 不打印堆栈，只记 warn 日志，避免日志被正常业务分支淹没。
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    /** 需要带回前端的附加信息，例如 409 时的已存在答卷 ID */
    private final Object data;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.data = null;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
        this.data = null;
    }

    public BizException(ResultCode resultCode, String message, Object data) {
        super(message);
        this.resultCode = resultCode;
        this.data = data;
    }
}
