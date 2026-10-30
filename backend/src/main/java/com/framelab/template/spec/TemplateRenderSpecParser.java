package com.framelab.template.spec;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.framelab.template.TemplateKind;
import org.springframework.stereotype.Component;

@Component
public class TemplateRenderSpecParser {

    private static final int MAX_SPEC_BYTES = 16 * 1024;

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public TemplateRenderSpec parse(String json) {
        if (json == null || json.isBlank()) {
            throw new TemplateSpecException("render spec is empty");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SPEC_BYTES) {
            throw new TemplateSpecException("render spec exceeds 16KB");
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new TemplateSpecException("render spec must be a JSON object");
            }
            JsonNode kindNode = root.get("kind");
            if (kindNode == null || !kindNode.isTextual()) {
                throw new TemplateSpecException("render spec kind is missing");
            }
            TemplateKind kind;
            try {
                kind = TemplateKind.valueOf(kindNode.asText());
            } catch (IllegalArgumentException ex) {
                throw new TemplateSpecException("unsupported render spec kind");
            }
            return switch (kind) {
                case IMAGE -> mapper.treeToValue(root, ImageRenderSpec.class);
                case VIDEO -> mapper.treeToValue(root, VideoRenderSpec.class);
            };
        } catch (JsonProcessingException ex) {
            throw new TemplateSpecException("render spec is malformed", ex);
        }
    }
}
