package com.example.oes.common.util;

import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.ResultCode;

import java.math.BigDecimal;

/**
 * 分值换算。系统内部一律用 int 表示"0.01 分"，杜绝浮点误差；
 * 对外（接口、Excel）用两位小数的分数，进入系统时立即转成整数。
 */
public final class ScoreUtil {

    private ScoreUtil() {
    }

    public static int toCent(BigDecimal yuan) {
        if (yuan == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "分值不能为空");
        }
        try {
            // intValueExact 在超过两位小数时抛异常，避免静默截断
            return yuan.movePointRight(2).intValueExact();
        } catch (ArithmeticException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "分值最多两位小数");
        }
    }

    public static BigDecimal toYuan(int cent) {
        return BigDecimal.valueOf(cent, 2);
    }
}
