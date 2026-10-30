package com.framelab.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.framelab.simulation.SimulationService;
import com.framelab.task.TaskClaimService;
import com.framelab.task.TaskStepService;
import com.framelab.task.TaskSubmissionService;
import com.framelab.task.worker.StartupRecoveryService;
import com.framelab.task.worker.TaskPipeline;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

abstract class TaskTestSupport extends ApiIntegrationTestSupport {

    @Autowired
    protected TaskClaimService taskClaimService;

    @Autowired
    protected TaskPipeline taskPipeline;

    @Autowired
    protected TaskSubmissionService taskSubmissionService;

    @Autowired
    protected TaskStepService taskStepService;

    @Autowired
    protected SimulationService simulationService;

    @Autowired
    protected StartupRecoveryService startupRecoveryService;

    @Autowired
    protected SqlSessionTemplate sqlSessionTemplate;

    protected String uploadJpeg(LoginResult login, String templateId, int width, int height, Color color)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "material-" + UUID.randomUUID() + ".jpg",
                "image/jpeg", jpegBytes(width, height, color));
        var request = multipart("/api/assets")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, bearer(login));
        if (templateId != null) {
            request.param("templateId", templateId);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Upload failed: " + result.getResponse().getContentAsString());
        }
        return json(result).path("id").asText();
    }

    protected MvcResult submitRaw(LoginResult login, String templateId, List<String> assetIds,
                                  String clientRequestId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "templateId", templateId,
                "assetIds", assetIds,
                "clientRequestId", clientRequestId));
        return mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    protected JsonNode submitTask(LoginResult login, String templateId, List<String> assetIds,
                                  String clientRequestId) throws Exception {
        MvcResult result = submitRaw(login, templateId, assetIds, clientRequestId);
        if (result.getResponse().getStatus() != 202) {
            throw new IllegalStateException("Submit failed: " + result.getResponse().getContentAsString());
        }
        return json(result);
    }

    protected void zeroSimulationDelays() {
        jdbcTemplate.update("""
                UPDATE simulation_setting
                   SET material_check_delay_ms = 0,
                       generate_delay_ms = 0,
                       finalize_delay_ms = 0
                 WHERE id = 1
                """);
        clearMapperCache();
    }

    protected void injectFailure(String stage, int remaining, boolean retryable) {
        jdbcTemplate.update("""
                UPDATE simulation_setting
                   SET material_check_delay_ms = 0,
                       generate_delay_ms = 0,
                       finalize_delay_ms = 0,
                       fail_stage = ?,
                       fail_times = ?,
                       fail_retryable = ?
                 WHERE id = 1
                """, stage, remaining, retryable);
        clearMapperCache();
    }

    protected void runQueuedTasks() {
        TaskClaimService.ClaimedStep claim;
        while ((claim = taskClaimService.claimNext()) != null) {
            taskPipeline.run(claim);
        }
    }

    protected void makeRetryDue(String taskId) {
        jdbcTemplate.update("""
                UPDATE task
                   SET next_retry_at = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND)
                 WHERE id = ?
                """, taskId);
        clearMapperCache();
    }

    protected void clearMapperCache() {
        sqlSessionTemplate.clearCache();
    }

    protected JsonNode taskDetail(LoginResult login, String taskId, String acceptLanguage) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/tasks/" + taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Detail failed: " + result.getResponse().getContentAsString());
        }
        return json(result);
    }

    protected static byte[] jpegBytes(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }

    protected static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
