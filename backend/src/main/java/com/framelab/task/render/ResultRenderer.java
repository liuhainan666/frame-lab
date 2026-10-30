package com.framelab.task.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

@Component
public class ResultRenderer {

    private static final String FONT_RESOURCE = "/fonts/Inter-SemiBold.ttf";
    private static final float JPEG_QUALITY = 0.9f;

    private volatile Font baseFont;

    public byte[] render(List<BufferedImage> images, int width, int height,
                         List<LayoutSlot> slots, String taskId) {
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
                drawCover(graphics, images.get(index), slots.get(index));
            }
            if (images.size() == 1) {
                drawBottomShade(graphics, width, height);
            }
            drawWatermark(graphics, width, height, taskId);
        } finally {
            graphics.dispose();
        }
        return encodeJpeg(canvas);
    }

    private void drawCover(Graphics2D graphics, BufferedImage image, LayoutSlot slot) {
        double scale = Math.max((double) slot.width() / image.getWidth(),
                (double) slot.height() / image.getHeight());
        int drawWidth = Math.max(1, (int) Math.ceil(image.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.ceil(image.getHeight() * scale));
        int x = slot.x() + (slot.width() - drawWidth) / 2;
        int y = slot.y() + (slot.height() - drawHeight) / 2;

        Shape previousClip = graphics.getClip();
        graphics.setClip(slot.x(), slot.y(), slot.width(), slot.height());
        graphics.drawImage(image, x, y, drawWidth, drawHeight, null);
        graphics.setClip(previousClip);
    }

    void drawBottomShade(Graphics2D graphics, int width, int height) {
        int start = (int) Math.round(height * 0.80);
        graphics.setPaint(new GradientPaint(0, start, new Color(0, 0, 0, 0),
                0, height, new Color(0, 0, 0, 190)));
        graphics.fillRect(0, start, width, height - start);
    }

    void drawWatermark(Graphics2D graphics, int width, int height, String taskId) {
        float fontSize = Math.max(14f, height * 0.018f);
        Font font = font().deriveFont(Font.PLAIN, fontSize);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        String shortId = taskId.substring(0, Math.min(8, taskId.length()));
        String text = "FRAMELAB · " + shortId;
        int padding = Math.max(16, Math.round(width * 0.025f));
        int x = Math.max(padding, width - padding - metrics.stringWidth(text));
        int y = Math.max(Math.round(fontSize), height - padding);

        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(new Color(255, 255, 255, 235));
        graphics.drawString(text, x, y);
    }

    private Font font() {
        Font font = baseFont;
        if (font == null) {
            synchronized (this) {
                font = baseFont;
                if (font == null) {
                    try (InputStream input = ResultRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
                        if (input == null) {
                            throw new IllegalStateException("Bundled font is missing");
                        }
                        font = Font.createFont(Font.TRUETYPE_FONT, input);
                        baseFont = font;
                    } catch (IOException | java.awt.FontFormatException ex) {
                        throw new IllegalStateException("Unable to load bundled font", ex);
                    }
                }
            }
        }
        return font;
    }

    byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode result image", ex);
        } finally {
            writer.dispose();
        }
    }
}
