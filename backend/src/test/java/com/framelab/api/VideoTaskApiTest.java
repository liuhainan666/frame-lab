package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.framelab.task.render.video.VideoRenderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class VideoTaskApiTest extends TaskTestSupport {

    private static final String TEMPLATE_ID = "test-video-task";

    @Autowired
    VideoRenderService videoRenderService;

    @Test
    void rendersVideoAndServesRangeAndPoster() throws Exception {
        assumeTrue(videoRenderService.isAvailable(), "ffmpeg is required for video tasks");
        jdbcTemplate.update("""
                INSERT INTO template
                  (id, category_id, kind, title, description, cover_path, required_image_count,
                   accepted_mime_types, max_file_size_bytes, render_spec, effect_tags, output_kind,
                   canvas_width, canvas_height, sort_order, is_published)
                VALUES (?, 'travel', 'VIDEO', '测试视频', '用于视频流水线验收',
                        '/assets/templates/city-walk-15s.webp', 3,
                        'image/jpeg,image/png', 10485760, ?, '["warm"]', 'VIDEO',
                        360, 640, 900, 1)
                """, TEMPLATE_ID, """
                {
                  "version": 1,
                  "kind": "VIDEO",
                  "durationMs": 3000,
                  "fps": 24,
                  "resolution": { "width": 360, "height": 640 },
                  "slots": [
                    { "seq": 1, "durationMs": 1200, "transition": "fade", "transitionMs": 300, "motion": "zoom-in" },
                    { "seq": 2, "durationMs": 1200, "transition": "slide-left", "transitionMs": 300, "motion": "pan-right" },
                    { "seq": 3, "durationMs": 1200, "transition": "fade", "transitionMs": 300, "motion": "none" }
                  ],
                  "look": {
                    "preset": "test-video",
                    "lut": "luts/city-warm-v1.cube",
                    "lutStrength": 0.5,
                    "exposure": 0.04,
                    "contrast": 1.04,
                    "saturation": 1.02,
                    "vignette": 0.1,
                    "grain": 0.02
                  },
                  "audio": { "path": "audio/city-walk-v1.mp3", "volume": 0.5, "fadeOutMs": 300 },
                  "text": [],
                  "watermark": true
                }
                """);
        zeroSimulationDelays();
        LoginResult login = login("demo@framelab.local", "Demo1234!");

        List<String> assetIds = List.of(
                uploadJpeg(login, TEMPLATE_ID, 320, 240, Color.RED),
                uploadJpeg(login, TEMPLATE_ID, 240, 320, Color.GREEN),
                uploadJpeg(login, TEMPLATE_ID, 320, 240, Color.BLUE));
        JsonNode submitted = submitTask(login, TEMPLATE_ID, assetIds, newRequestId());
        String taskId = submitted.path("id").asText();

        runQueuedTasks();

        JsonNode task = taskDetail(login, taskId, "zh-CN");
        assertThat(task.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(task.path("result").path("kind").asText()).isEqualTo("VIDEO");
        assertThat(task.path("result").path("mimeType").asText()).isEqualTo("video/mp4");
        assertThat(task.path("result").path("durationMs").asInt()).isEqualTo(3000);
        assertThat(task.path("result").path("width").asInt()).isEqualTo(360);
        assertThat(task.path("result").path("height").asInt()).isEqualTo(640);
        assertThat(task.path("result").path("posterUrl").asText())
                .isEqualTo("/api/tasks/" + taskId + "/result/poster");

        MvcResult full = mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andReturn();
        assertThat(full.getResponse().getContentAsByteArray()).isNotEmpty();

        MvcResult partial = mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .header(HttpHeaders.RANGE, "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE,
                        org.hamcrest.Matchers.startsWith("bytes 0-99/")))
                .andReturn();
        assertThat(partial.getResponse().getContentAsByteArray()).hasSize(100);

        mockMvc.perform(get("/api/tasks/" + taskId + "/result/poster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));
    }

    @Test
    void rejectsVideoSubmissionWhenTemplateIsMissingSpec() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO template
                  (id, category_id, kind, title, description, cover_path, required_image_count,
                   accepted_mime_types, max_file_size_bytes, output_kind,
                   canvas_width, canvas_height, sort_order, is_published)
                VALUES ('test-video-broken', 'travel', 'VIDEO', '损坏视频', '缺少渲染配置',
                        '/assets/templates/city-walk-15s.webp', 3,
                        'image/jpeg,image/png', 10485760, 'VIDEO',
                        360, 640, 901, 1)
                """);
        zeroSimulationDelays();
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        List<String> assetIds = List.of(
                uploadJpeg(login, "test-video-broken", 320, 240, Color.RED),
                uploadJpeg(login, "test-video-broken", 320, 240, Color.GREEN),
                uploadJpeg(login, "test-video-broken", 320, 240, Color.BLUE));

        MvcResult result = submitRaw(login, "test-video-broken", assetIds, newRequestId());

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(problemCode(result)).isEqualTo("TEMPLATE_SPEC_INVALID");
    }
}
