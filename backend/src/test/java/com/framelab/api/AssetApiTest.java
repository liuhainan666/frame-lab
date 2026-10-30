package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class AssetApiTest extends ApiIntegrationTestSupport {

    @Test
    void uploadsJpegWithPrivateStorageAndReadableContent() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        MvcResult upload = upload(login, jpeg(100, 100), "image/jpeg", "holiday-photo.jpg", "travel-postcard");
        assertThat(upload.getResponse().getStatus()).isEqualTo(201);

        JsonNode body = json(upload);
        String id = body.path("id").asText();
        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(body.path("mimeType").asText()).isEqualTo("image/jpeg");
        assertThat(body.path("width").asInt()).isEqualTo(100);
        assertThat(body.path("height").asInt()).isEqualTo(100);
        assertThat(body.path("sizeBytes").asLong()).isPositive();
        assertThat(body.path("contentUrl").asText()).isEqualTo("/api/assets/" + id + "/content");
        assertThat(upload.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/assets/" + id + "/content");

        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM upload_asset WHERE id = ?", String.class, id);
        assertThat(storageKey).startsWith("materials/1/")
                .endsWith(".jpg")
                .doesNotContain("holiday-photo");
        Path stored = STORAGE_ROOT.resolve(storageKey);
        assertThat(stored).exists();
        assertThat(Files.size(stored)).isEqualTo(body.path("sizeBytes").asLong());

        MvcResult content = mockMvc.perform(get("/api/assets/" + id + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"" + id + "\""))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("asset-" + id + ".jpg")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("private")))
                .andReturn();

        byte[] contentBytes = content.getResponse().getContentAsByteArray();
        assertThat(contentBytes[0]).isEqualTo((byte) 0xFF);
        assertThat(contentBytes[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void uploadsPngAndReturnsPngContentType() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        MvcResult upload = upload(login, png(80, 60), "image/png", "graphic.png", "travel-postcard");
        assertThat(upload.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json(upload);
        String id = body.path("id").asText();
        assertThat(body.path("mimeType").asText()).isEqualTo("image/png");

        mockMvc.perform(get("/api/assets/" + id + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("asset-" + id + ".png")));
    }

    @Test
    void supportsConditionalContentRead() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String id = json(upload(login, jpeg(40, 40), "image/jpeg", "small.jpg", "travel-postcard"))
                .path("id").asText();

        MvcResult first = mockMvc.perform(get("/api/assets/" + id + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/assets/" + id + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void rejectsFakeSvgAndOversizedFiles() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");

        MvcResult fake = upload(login, "this is not an image".getBytes(StandardCharsets.UTF_8),
                "image/jpeg", "fake.jpg", "travel-postcard");
        assertThat(fake.getResponse().getStatus()).isEqualTo(422);
        assertThat(problemCode(fake)).isEqualTo("INVALID_IMAGE");

        MvcResult svg = upload(login, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>"
                .getBytes(StandardCharsets.UTF_8), "image/svg+xml", "vector.svg", "travel-postcard");
        assertThat(svg.getResponse().getStatus()).isEqualTo(415);
        assertThat(problemCode(svg)).isEqualTo("UNSUPPORTED_MEDIA_TYPE");

        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        tooLarge[0] = (byte) 0xFF;
        tooLarge[1] = (byte) 0xD8;
        tooLarge[2] = (byte) 0xFF;
        MvcResult oversized = upload(login, tooLarge, "image/jpeg", "huge.jpg", "travel-postcard");
        assertThat(oversized.getResponse().getStatus()).isEqualTo(413);
        assertThat(problemCode(oversized)).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void rejectsEmptyFilesAndUnknownTemplates() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");

        MvcResult empty = upload(login, new byte[0], "image/jpeg", "empty.jpg", "travel-postcard");
        assertThat(empty.getResponse().getStatus()).isEqualTo(400);
        assertThat(problemCode(empty)).isEqualTo("VALIDATION_FAILED");

        MvcResult missingTemplate = upload(login, jpeg(20, 20), "image/jpeg", "ok.jpg", "no-such-template");
        assertThat(missingTemplate.getResponse().getStatus()).isEqualTo(404);
        assertThat(problemCode(missingTemplate)).isEqualTo("TEMPLATE_NOT_FOUND");

        MvcResult invalidTemplate = upload(login, jpeg(20, 20), "image/jpeg", "ok.jpg", "../escape");
        assertThat(invalidTemplate.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(invalidTemplate).path("errors").get(0).path("field").asText()).isEqualTo("templateId");
    }

    @Test
    void enforcesOwnershipForContentAndDelete() throws Exception {
        LoginResult owner = login("demo@framelab.local", "Demo1234!");
        String id = json(upload(owner, jpeg(30, 30), "image/jpeg", "own.jpg", "travel-postcard"))
                .path("id").asText();
        LoginResult other = login("demo2@framelab.local", "Demo1234!");

        mockMvc.perform(get("/api/assets/" + id + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));

        mockMvc.perform(delete("/api/assets/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/assets/" + id + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void deletesUnboundAssetAndFile() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String id = json(upload(login, jpeg(25, 25), "image/jpeg", "delete.jpg", "travel-postcard"))
                .path("id").asText();
        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM upload_asset WHERE id = ?", String.class, id);

        mockMvc.perform(delete("/api/assets/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isNoContent());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM upload_asset WHERE id = ?", Integer.class, id);
        assertThat(count).isZero();
        assertThat(STORAGE_ROOT.resolve(storageKey)).doesNotExist();
    }

    @Test
    void refusesToDeleteAssetReferencedByTask() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String id = json(upload(login, jpeg(30, 30), "image/jpeg", "used.jpg", "travel-postcard"))
                .path("id").asText();
        String taskId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                        INSERT INTO task
                          (id, user_id, template_id, template_title, template_cover_path,
                           canvas_width, canvas_height, client_request_id, status, max_step_attempts)
                        VALUES (?, 1, 'travel-postcard', '旅行明信片', '/assets/templates/travel-postcard.webp',
                                1600, 1200, ?, 'QUEUED', 3)
                        """, taskId, UUID.randomUUID().toString());
        jdbcTemplate.update("INSERT INTO task_material (task_id, slot_index, asset_id) VALUES (?, 1, ?)",
                taskId, id);

        mockMvc.perform(delete("/api/assets/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSET_IN_USE"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM upload_asset WHERE id = ?", Integer.class, id);
        assertThat(count).isOne();
    }

    @Test
    void missingStoredFileReturnsServiceUnavailable() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String id = json(upload(login, jpeg(20, 20), "image/jpeg", "lost.jpg", "travel-postcard"))
                .path("id").asText();
        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM upload_asset WHERE id = ?", String.class, id);
        Files.delete(STORAGE_ROOT.resolve(storageKey));

        mockMvc.perform(get("/api/assets/" + id + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void contentRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/assets/" + UUID.randomUUID() + "/content"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    private MvcResult upload(LoginResult login, byte[] bytes, String contentType, String filename,
                             String templateId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, bytes);
        MockMultipartHttpServletRequestBuilder request = multipart("/api/assets").file(file);
        if (templateId != null) {
            request.param("templateId", templateId);
        }
        request.header(HttpHeaders.AUTHORIZATION, bearer(login));
        return mockMvc.perform(request).andReturn();
    }

    private byte[] jpeg(int width, int height) throws IOException {
        return image("jpg", width, height, BufferedImage.TYPE_INT_RGB, Color.ORANGE);
    }

    private byte[] png(int width, int height) throws IOException {
        return image("png", width, height, BufferedImage.TYPE_INT_ARGB, new Color(20, 120, 200, 160));
    }

    private byte[] image(String format, int width, int height, int type, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
