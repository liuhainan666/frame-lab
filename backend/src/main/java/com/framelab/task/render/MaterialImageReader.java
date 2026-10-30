package com.framelab.task.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.framelab.asset.AssetContent;
import com.framelab.asset.AssetService;
import org.springframework.stereotype.Component;

@Component
public class MaterialImageReader {

    private final AssetService assetService;

    public MaterialImageReader(AssetService assetService) {
        this.assetService = assetService;
    }

    public BufferedImage read(long userId, String assetId) throws IOException {
        AssetContent content = assetService.content(userId, assetId);
        try (InputStream input = content.resource().getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Unreadable material image");
            }
            return image;
        }
    }
}
