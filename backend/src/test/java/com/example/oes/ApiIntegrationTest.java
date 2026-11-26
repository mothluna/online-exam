package com.example.oes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口级集成测试（真实 MySQL + Redis，独立 oes_test 库与 Redis DB 15）。
 *
 * <p>覆盖"认证矩阵 + 建题→组卷→开考→交卷→成绩→复核"主链路。
 * 每个测试方法在事务内执行并回滚；题干与标题都带 UUID，
 * 因此重复执行 mvn test 不会因唯一键冲突而失败。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate redis;

    private String adminToken;
    private String teacherToken;
    private String studentToken;

    @BeforeAll
    void setUp() throws Exception {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table : List.of("attempt_answer", "exam_attempt", "exam",
                "paper_question", "paper_rule", "paper", "question", "question_category")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");

        // 登录失败计数会跨测试运行累积，5 次后触发锁定，先清掉
        var failKeys = redis.keys("oes:login:fail:*");
        if (failKeys != null && !failKeys.isEmpty()) {
            redis.delete(failKeys);
        }

        adminToken = login("admin");
        teacherToken = login("teacher");
        studentToken = login("student");
    }

    // ---------------- 认证 ----------------

    @Test
    @DisplayName("认证矩阵：无令牌 401、学生越权 403、登出后令牌 401/40102、错误密码 401")
    void authMatrix() throws Exception {
        mockMvc.perform(get("/api/admin/questions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/admin/questions").header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TEACHER"));

        // 登出后旧令牌必须立即失效
        String token = login("student");
        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));

        mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(json(Map.of("username", "student", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- 主链路：客观题 ----------------

    @Test
    @DisplayName("客观题链路：建题→抽题→开考→交卷，含重复题干 409、总分不符 422、重复开考 409")
    void objectiveExamFlow() throws Exception {
        long categoryId = postForId("/api/admin/categories", teacherToken,
                Map.of("name", "集成测试分类-" + UUID.randomUUID()));

        String stemPrefix = "集成测试题干-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            postForId("/api/admin/questions", teacherToken, singleChoice(categoryId, stemPrefix + "-" + i));
        }

        // 只差全角问号与首尾空格，归一化后与第 0 题相同 → 必须拒绝
        mockMvc.perform(post("/api/admin/questions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(JSON)
                        .content(json(singleChoice(categoryId, "  " + stemPrefix + "-0？  "))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));

        // 规则合计 5×2=10，声明 11 → 422 并给出差额
        Map<String, Object> wrongPaper = paperPayload(11.00);
        mockMvc.perform(post("/api/admin/papers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(JSON)
                        .content(json(wrongPaper)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        long paperId = postForId("/api/admin/papers", teacherToken, paperPayload(10.00));
        mockMvc.perform(post("/api/admin/papers/{id}/randomize", paperId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/papers/{id}/publish", paperId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk());

        long examId = postForId("/api/admin/exams", teacherToken, examPayload(paperId));
        mockMvc.perform(post("/api/admin/exams/{id}/publish", examId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk());

        // 开考：题目不下发答案
        MvcResult startResult = mockMvc.perform(post("/api/student/exams/{id}/start", examId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode start = readData(startResult);
        long attemptId = start.path("attemptId").asLong();
        assertEquals(5, start.path("questions").size());
        assertFalse(start.path("questions").get(0).has("answer"), "开考响应不得包含答案字段");

        // 重复开考 → 409 + 已有 attemptId
        MvcResult duplicate = mockMvc.perform(post("/api/student/exams/{id}/start", examId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andReturn();
        assertEquals(attemptId, readData(duplicate).path("attemptId").asLong());

        // 暂存 + 交卷
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode q : start.path("questions")) {
            items.add(Map.of("questionId", q.path("questionId").asLong(), "answer", List.of("A")));
        }
        mockMvc.perform(put("/api/student/attempts/{id}/answers", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                        .contentType(JSON)
                        .content(json(Map.of("items", items))))
                .andExpect(status().isOk());

        MvcResult submitResult = mockMvc.perform(post("/api/student/attempts/{id}/submit", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode submit = readData(submitResult);
        assertEquals("GRADED", submit.path("status").asText());
        assertEquals(10.00, submit.path("totalScore").asDouble(), 0.001);
        assertEquals(1, submit.path("isPass").asInt());

        // 考试未结束：成绩单只给总分，逐题得分与答案锁定
        MvcResult result = mockMvc.perform(get("/api/student/attempts/{id}/result", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resultData = readData(result);
        assertFalse(resultData.path("answerVisible").asBoolean());
        JsonNode firstQuestion = resultData.path("questions").get(0);
        // non_null 序列化会把 null 字段整个省略，所以既要判 null 也要判字段缺失
        assertTrue(firstQuestion.path("score").isMissingNode() || firstQuestion.path("score").isNull(),
                "未结束时逐题得分不得下发");
        assertTrue(firstQuestion.path("correctAnswer").isMissingNode() || firstQuestion.path("correctAnswer").isNull(),
                "未结束时正确答案不得下发");

        // 教师视角能看到答案与判分依据，但客观题不允许人工复核
        MvcResult detail = mockMvc.perform(get("/api/admin/attempts/{id}/detail", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        long answerId = readData(detail).path("answers").get(0).path("answerId").asLong();
        mockMvc.perform(put("/api/admin/answers/{id}/review", answerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(JSON)
                        .content(json(Map.of("score", 1.00))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    // ---------------- 主观题复核 ----------------

    @Test
    @DisplayName("主观题复核：机器初判 6.66 → 教师改 8.00，总分与 needReview 同步")
    void reviewFlow() throws Exception {
        long categoryId = postForId("/api/admin/categories", teacherToken,
                Map.of("name", "复核测试分类-" + UUID.randomUUID()));

        Map<String, Object> question = new LinkedHashMap<>();
        question.put("categoryId", categoryId);
        question.put("type", "SHORT");
        question.put("stem", "简述 JVM 如何判断对象可回收-" + UUID.randomUUID());
        question.put("answer", List.of("可达性分析", "分代", "回收"));
        question.put("defaultScore", 10.00);
        question.put("difficulty", 2);
        postForId("/api/admin/questions", teacherToken, question);

        Map<String, Object> paper = paperPayload(10.00);
        paper.put("rules", List.of(Map.of("type", "SHORT", "questionCount", 1, "scorePerItem", 10.00)));
        long paperId = postForId("/api/admin/papers", teacherToken, paper);
        mockMvc.perform(post("/api/admin/papers/{id}/randomize", paperId)
                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/papers/{id}/publish", paperId)
                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))).andExpect(status().isOk());

        long examId = postForId("/api/admin/exams", teacherToken, examPayload(paperId));
        mockMvc.perform(post("/api/admin/exams/{id}/publish", examId)
                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))).andExpect(status().isOk());

        MvcResult startResult = mockMvc.perform(post("/api/student/exams/{id}/start", examId)
                .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))).andExpect(status().isOk()).andReturn();
        JsonNode start = readData(startResult);
        long attemptId = start.path("attemptId").asLong();
        long questionId = start.path("questions").get(0).path("questionId").asLong();

        String answer = "通过可达性分析判断对象是否存活，再按分代策略处理内存。";
        mockMvc.perform(put("/api/student/attempts/{id}/answers", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                        .contentType(JSON)
                        .content(json(Map.of("items", List.of(Map.of("questionId", questionId, "answer", List.of(answer)))))))
                .andExpect(status().isOk());

        MvcResult submitResult = mockMvc.perform(post("/api/student/attempts/{id}/submit", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode submit = readData(submitResult);
        // 命中 2/3 关键词：1000 × 2 / 3 = 666
        assertEquals(6.66, submit.path("totalScore").asDouble(), 0.001);
        assertTrue(submit.path("needReview").asBoolean());

        MvcResult detail = mockMvc.perform(get("/api/admin/attempts/{id}/detail", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detailData = readData(detail);
        assertEquals(1, detailData.path("pendingReviewCount").asInt());
        long answerId = detailData.path("answers").get(0).path("answerId").asLong();

        MvcResult review = mockMvc.perform(put("/api/admin/answers/{id}/review", answerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(JSON)
                        .content(json(Map.of("score", 8.00, "comment", "要点完整，表述清晰"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reviewData = readData(review);
        assertEquals(8.00, reviewData.path("totalScore").asDouble(), 0.001);
        assertEquals(1, reviewData.path("isPass").asInt());
        assertEquals(0, reviewData.path("pendingReviewCount").asInt());

        // 学生成绩单：复核后不再提示待复核
        MvcResult result = mockMvc.perform(get("/api/student/attempts/{id}/result", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resultData = readData(result);
        assertEquals(8.00, resultData.path("totalScore").asDouble(), 0.001);
        assertFalse(resultData.path("needReview").asBoolean());
    }

    // ---------------- 辅助方法 ----------------

    private Map<String, Object> singleChoice(long categoryId, String stem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("type", "SINGLE");
        body.put("stem", stem);
        body.put("options", List.of(
                Map.of("key", "A", "content", "选项 A"),
                Map.of("key", "B", "content", "选项 B")));
        body.put("answer", List.of("A"));
        body.put("defaultScore", 2.00);
        body.put("difficulty", 2);
        return body;
    }

    private Map<String, Object> paperPayload(double totalScore) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "集成测试试卷-" + UUID.randomUUID());
        body.put("composeType", "RANDOM");
        body.put("totalScore", totalScore);
        body.put("durationSec", 1800);
        body.put("passScore", 5.00);
        body.put("rules", List.of(Map.of("type", "SINGLE", "questionCount", 5, "scorePerItem", 2.00)));
        return body;
    }

    private Map<String, Object> examPayload(long paperId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paperId", paperId);
        body.put("title", "集成测试考试-" + UUID.randomUUID());
        body.put("startTime", LocalDateTime.now().minusHours(1).format(TIME));
        body.put("endTime", LocalDateTime.now().plusHours(1).format(TIME));
        body.put("durationSec", 1800);
        body.put("allowRetake", 0);
        body.put("shuffle", 0);
        return body;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(json(Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return readData(result).path("token").asText();
    }

    private long postForId(String url, String token, Object payload) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(JSON)
                        .content(json(payload)))
                .andExpect(status().isOk())
                .andReturn();
        return readData(result).asLong();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
