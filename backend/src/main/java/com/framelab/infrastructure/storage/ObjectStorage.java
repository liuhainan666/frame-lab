package com.framelab.infrastructure.storage;

import org.springframework.core.io.Resource;

public interface ObjectStorage {

    StoredObject writeMaterial(long userId, String assetId, byte[] bytes, String extension);

    Resource open(String storageKey);

    void delete(String storageKey);
}
