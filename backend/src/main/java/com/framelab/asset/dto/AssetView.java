package com.framelab.asset.dto;

import java.time.Instant;

import com.framelab.asset.UploadAsset;
import com.framelab.common.time.TimeFormat;

public record AssetView(
        String id,
        String mimeType,
        long sizeBytes,
        int width,
        int height,
        Instant createdAt,
        String contentUrl) {

    public static AssetView from(UploadAsset asset) {
        return new AssetView(
                asset.getId(),
                asset.getMimeType(),
                asset.getSizeBytes(),
                asset.getWidth(),
                asset.getHeight(),
                TimeFormat.toInstant(asset.getCreatedAt()),
                "/api/assets/" + asset.getId() + "/content");
    }
}
