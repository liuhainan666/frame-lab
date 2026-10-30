package com.framelab.asset;

import org.springframework.core.io.Resource;

public record AssetContent(UploadAsset asset, Resource resource) {
}
