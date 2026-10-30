package com.framelab.task.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class LutRegistry {

    private final ConcurrentHashMap<String, CubeLut> cache = new ConcurrentHashMap<>();

    public CubeLut load(String resourceKey) {
        if (resourceKey == null || resourceKey.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(resourceKey, this::read);
    }

    private CubeLut read(String resourceKey) {
        String path = "templates/" + resourceKey;
        try (InputStream input = LutRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing LUT resource: " + resourceKey);
            }
            return CubeLut.parse(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read LUT resource: " + resourceKey, ex);
        }
    }
}
