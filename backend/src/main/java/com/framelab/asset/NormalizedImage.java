package com.framelab.asset;

public record NormalizedImage(byte[] bytes, String mimeType, String extension, int width, int height) {
}
