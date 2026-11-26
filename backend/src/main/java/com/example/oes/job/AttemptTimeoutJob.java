package com.example.oes.job;

import com.example.oes.mapper.ExamAttemptMapper;
import com.example.oes.service.JudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时自动交卷。
 * 必须有这个：考生直接关浏览器就不会发提交请求，只靠"提交时校验"的话，
 * 答卷会永远停留在 IN_PROGRESS，统计口径直接错掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttemptTimeoutJob {

    private static final int BATCH = 200;

    private final ExamAttemptMapper attemptMapper;
    private final JudgeService judgeService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void autoSubmitExpired() {
        // 只捞 deadline 已过的，绝不批量处理全部进行中的答卷
        List<Long> expiredIds = attemptMapper.selectExpiredIds(BATCH);
        if (expiredIds.isEmpty()) {
            return;
        }
        int submitted = 0;
        for (Long id : expiredIds) {
            // 仍用 CAS（WHERE status='IN_PROGRESS'）：
            // 若考生恰好在同一时刻提交，以先到者为准，不重复处理
            if (attemptMapper.casTimeout(id) == 0) {
                continue;
            }
            try {
                judgeService.grade(id);
            } catch (Exception e) {
                // 判分失败不阻断后续答卷；该卷仍为 TIMEOUT，可由提交路径或人工重判补上
                log.error("auto grade failed: attemptId={}", id, e);
            }
            submitted++;
        }
        log.info("timeout scan: candidates={}, auto submitted={}", expiredIds.size(), submitted);
    }
}
