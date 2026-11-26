package com.example.oes.common.exception;

import com.example.oes.common.result.R;
import com.example.oes.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常出口。所有异常都必须落到统一响应体，且 HTTP 状态码与 code 前缀一致。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Object>> handleBiz(BizException e) {
        ResultCode rc = e.getResultCode();
        log.warn("biz error: code={}, msg={}", rc.getCode(), e.getMessage());
        R<Object> body = R.fail(rc, e.getMessage());
        body.setData(e.getData());
        return ResponseEntity.status(rc.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("param invalid: {}", detail);
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(R.fail(ResultCode.PARAM_INVALID, detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Object>> handleConstraint(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("constraint violation: {}", detail);
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(R.fail(ResultCode.PARAM_INVALID, detail));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<R<Object>> handleBadRequest(Exception e) {
        log.warn("bad request: {}", e.getMessage());
        return ResponseEntity.status(ResultCode.PARAM_INVALID.getHttpStatus())
                .body(R.fail(ResultCode.PARAM_INVALID, "请求参数格式不正确"));
    }

    /**
     * 唯一索引冲突。由数据库仲裁并发写入，应用层据此翻译为业务语义。
     * 具体归因（题干重复 / 重复开考）由各业务在 service 层捕获后抛出对应 BizException，
     * 走到这里的是未归因的兜底情况。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<R<Object>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("duplicate key: {}", e.getMessage());
        return ResponseEntity.status(ResultCode.DUPLICATE_STEM.getHttpStatus())
                .body(R.fail(ResultCode.DUPLICATE_STEM, "数据已存在，请勿重复提交"));
    }

    /**
     * 未匹配到任何 handler。
     * Spring 6.1 起无匹配路由会抛 NoResourceFoundException（不再返回默认 404 页面），
     * 若不单独处理会被下面的 Exception 兜底成 500 —— 必须在这里拦住。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Object>> handleNotFound(NoResourceFoundException e) {
        log.warn("no handler for: {} {}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity.status(ResultCode.NOT_FOUND.getHttpStatus())
                .body(R.fail(ResultCode.NOT_FOUND, "接口不存在: " + e.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Object>> handleOther(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getHttpStatus())
                .body(R.fail(ResultCode.SYSTEM_ERROR));
    }
}
