package com.framelab.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.framelab.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageTest {

    @TempDir
    Path root;

    @Test
    void writesResultAtomicallyAndRemovesTemporaryFile() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(properties());
        byte[] bytes = "jpeg-result".getBytes(StandardCharsets.UTF_8);
        String taskId = "0f0c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e44";

        StoredObject stored = storage.writeResult(7, taskId, bytes);

        assertThat(stored.storageKey()).isEqualTo("results/7/" + taskId + ".jpg");
        Path target = root.resolve(stored.storageKey());
        assertThat(target).exists();
        assertThat(Files.size(target)).isEqualTo(bytes.length);
        assertThat(storage.open(stored.storageKey()).getInputStream().readAllBytes()).isEqualTo(bytes);

        Path tempDirectory = root.resolve("tmp/task-" + taskId);
        if (Files.exists(tempDirectory)) {
            try (var files = Files.list(tempDirectory)) {
                assertThat(files.toList()).isEmpty();
            }
        }

        storage.deleteTaskTemp(taskId);
        assertThat(tempDirectory).doesNotExist();
    }

    @Test
    void deletesResultObject() {
        LocalObjectStorage storage = new LocalObjectStorage(properties());
        StoredObject stored = storage.writeResult(7, "task-1", new byte[] {1, 2, 3});

        storage.delete(stored.storageKey());

        assertThat(root.resolve(stored.storageKey())).doesNotExist();
    }

    private StorageProperties properties() {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(root);
        return properties;
    }
}
