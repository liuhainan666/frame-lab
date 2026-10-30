package com.framelab.task.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import com.framelab.template.spec.ImageRenderSpec;
import org.springframework.stereotype.Component;

@Component
public class EffectResultRenderer {

    private final ColorPipeline colorPipeline;
    private final PortraitProcessor portraitProcessor;
    private final GlobalEffects globalEffects;
    private final ResultRenderer resultRenderer;

    public EffectResultRenderer(ColorPipeline colorPipeline,
                                PortraitProcessor portraitProcessor,
                                GlobalEffects globalEffects,
                                ResultRenderer resultRenderer) {
        this.colorPipeline = colorPipeline;
        this.portraitProcessor = portraitProcessor;
        this.globalEffects = globalEffects;
        this.resultRenderer = resultRenderer;
    }

    public byte[] render(List<BufferedImage> images, ImageRenderSpec spec,
                         int width, int height, List<LayoutSlot> slots, String taskId) {
        if (images.size() != slots.size()) {
            throw new IllegalArgumentException("Image and slot counts differ");
        }
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(new Color(17, 17, 17));
            graphics.fillRect(0, 0, width, height);
            for (int index = 0; index < images.size(); index++) {
                LayoutSlot slot = slots.get(index);
                BufferedImage prepared = coverCrop(images.get(index), slot.width(), slot.height());
                colorPipeline.apply(prepared, spec.look());
                portraitProcessor.apply(prepared, spec.portrait());
                graphics.drawImage(prepared, slot.x(), slot.y(), null);
            }
            if (images.size() == 1) {
                resultRenderer.drawBottomShade(graphics, width, height);
            }
        } finally {
            graphics.dispose();
        }

        globalEffects.apply(canvas, spec.look(), taskId);
        if (spec.watermark()) {
            Graphics2D overlay = canvas.createGraphics();
            try {
                overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                resultRenderer.drawWatermark(overlay, width, height, taskId);
            } finally {
                overlay.dispose();
            }
        }
        return resultRenderer.encodeJpeg(canvas);
    }

    private BufferedImage coverCrop(BufferedImage source, int targetWidth, int targetHeight) {
        double scale = Math.max((double) targetWidth / source.getWidth(),
                (double) targetHeight / source.getHeight());
        int drawWidth = Math.max(1, (int) Math.ceil(source.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.ceil(source.getHeight() * scale));
        BufferedImage cropped = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = cropped.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, (targetWidth - drawWidth) / 2, (targetHeight - drawHeight) / 2,
                    drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }
        return cropped;
    }
}
