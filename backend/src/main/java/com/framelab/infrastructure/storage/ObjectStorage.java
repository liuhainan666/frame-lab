package com.framelab.infrastructure.storage;

import java.nio.file.Path;

import org.springframework.core.io.Resource;

public interface ObjectStorage {

    StoredObject writeMaterial(long userId, String assetId, byte[] bytes, String extension);

    StoredObject writeResult(long userId, String taskId, byte[] bytes);

    StoredObject writeResult(long userId, String taskId, byte[] bytes, String extension);

    StoredObject writeResultFile(long userId, String taskId, Path source, String extension);

    Resource open(String storageKey);

    void delete(String storageKey);

    void deleteTaskTemp(String taskId);
}
