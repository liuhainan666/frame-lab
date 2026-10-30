package com.framelab.asset;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.config.StorageProperties;
import org.springframework.stereotype.Component;

@Component
public class ImageNormalizer {

    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";

    private final StorageProperties properties;

    public ImageNormalizer(StorageProperties properties) {
        this.properties = properties;
    }

    public NormalizedImage normalize(byte[] bytes, String declaredMimeType, List<String> allowedMimeTypes) {
        if (bytes == null || bytes.length == 0) {
            throw invalidImage();
        }
        String declared = normalizeMime(declaredMimeType);
        if (!JPEG.equals(declared) && !PNG.equals(declared)) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        String detected = detect(bytes);
        if (detected == null || !detected.equals(declared)) {
            throw invalidImage();
        }
        List<String> allowed = allowedMimeTypes == null || allowedMimeTypes.isEmpty()
                ? List.of(JPEG, PNG)
                : allowedMimeTypes;
        if (!allowed.contains(detected)) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        BufferedImage decoded = decode(bytes, detected);
        int orientation = JPEG.equals(detected) ? jpegOrientation(bytes) : 1;
        BufferedImage oriented = applyOrientation(decoded, orientation);
        BufferedImage scaled = scaleDown(oriented);
        byte[] encoded = encode(scaled, detected);
        String extension = JPEG.equals(detected) ? "jpg" : "png";
        return new NormalizedImage(encoded, detected, extension, scaled.getWidth(), scaled.getHeight());
    }

    private BufferedImage decode(byte[] bytes, String mimeType) {
        String format = JPEG.equals(mimeType) ? "jpeg" : "png";
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || width > properties.getMaxDimension()
                        || height > properties.getMaxDimension()
                        || (long) width * height > properties.getMaxPixels()) {
                    throw invalidImage();
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidImage();
        }
    }

    private BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean swap = orientation >= 5;
        int outWidth = swap ? height : width;
        int outHeight = swap ? width : height;
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> null;
        };
        BufferedImage output = new BufferedImage(outWidth, outHeight, imageType(image));
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.drawImage(image, transform, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private BufferedImage scaleDown(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int longest = Math.max(width, height);
        if (longest <= properties.getMaxNormalizedDimension()) {
            return image;
        }
        double ratio = (double) properties.getMaxNormalizedDimension() / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        BufferedImage output = new BufferedImage(targetWidth, targetHeight, imageType(image));
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private byte[] encode(BufferedImage image, String mimeType) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (JPEG.equals(mimeType)) {
                BufferedImage rgb = toRgb(image);
                ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
                    writer.setOutput(stream);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.9f);
                    writer.write(null, new IIOImage(rgb, null, null), param);
                } finally {
                    writer.dispose();
                }
            } else {
                if (!ImageIO.write(image, "png", output)) {
                    throw invalidImage();
                }
            }
            return output.toByteArray();
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw invalidImage();
        }
    }

    private BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private int imageType(BufferedImage image) {
        return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    private String normalizeMime(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        int semicolon = mimeType.indexOf(';');
        String value = semicolon >= 0 ? mimeType.substring(0, semicolon) : mimeType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String detect(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return PNG;
        }
        return null;
    }

    private int jpegOrientation(byte[] bytes) {
        if (bytes.length < 4 || (bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int position = 2;
        while (position + 4 <= bytes.length) {
            int marker = bytes[position] & 0xFF;
            if (marker != 0xFF) {
                position++;
                continue;
            }
            int type = bytes[position + 1] & 0xFF;
            if (type == 0xFF) {
                position++;
                continue;
            }
            if (type == 0xD8 || type == 0x01 || (type >= 0xD0 && type <= 0xD7)) {
                position += 2;
                continue;
            }
            if (type == 0xDA || type == 0xD9) {
                break;
            }
            int length = readU16BigEndian(bytes, position + 2);
            if (length < 2 || position + 2 + length > bytes.length) {
                break;
            }
            int dataStart = position + 4;
            if (type == 0xE1 && length >= 8
                    && bytes[dataStart] == 'E' && bytes[dataStart + 1] == 'x'
                    && bytes[dataStart + 2] == 'i' && bytes[dataStart + 3] == 'f'
                    && bytes[dataStart + 4] == 0 && bytes[dataStart + 5] == 0) {
                int orientation = parseExifOrientation(bytes, dataStart + 6, position + 2 + length);
                if (orientation != 1) {
                    return orientation;
                }
            }
            position += 2 + length;
        }
        return 1;
    }

    private int parseExifOrientation(byte[] bytes, int tiffStart, int end) {
        if (tiffStart + 8 > end) {
            return 1;
        }
        boolean littleEndian;
        if (bytes[tiffStart] == 'I' && bytes[tiffStart + 1] == 'I') {
            littleEndian = true;
        } else if (bytes[tiffStart] == 'M' && bytes[tiffStart + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        if (readU16(bytes, tiffStart + 2, littleEndian) != 42) {
            return 1;
        }
        long ifdOffset = readU32(bytes, tiffStart + 4, littleEndian);
        long ifdPosition = tiffStart + ifdOffset;
        if (ifdPosition < tiffStart || ifdPosition > Integer.MAX_VALUE || ifdPosition + 2 > end) {
            return 1;
        }
        int entryCount = readU16(bytes, (int) ifdPosition, littleEndian);
        for (int i = 0; i < entryCount; i++) {
            int entry = (int) ifdPosition + 2 + i * 12;
            if (entry + 12 > end) {
                break;
            }
            int tag = readU16(bytes, entry, littleEndian);
            if (tag == 0x0112) {
                int type = readU16(bytes, entry + 2, littleEndian);
                long components = readU32(bytes, entry + 4, littleEndian);
                if (type == 3 && components >= 1) {
                    int orientation = readU16(bytes, entry + 8, littleEndian);
                    return orientation >= 1 && orientation <= 8 ? orientation : 1;
                }
            }
        }
        return 1;
    }

    private int readU16BigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private int readU16(byte[] bytes, int offset, boolean littleEndian) {
        if (offset + 2 > bytes.length) {
            return 0;
        }
        int first = bytes[offset] & 0xFF;
        int second = bytes[offset + 1] & 0xFF;
        return littleEndian ? (second << 8) | first : (first << 8) | second;
    }

    private long readU32(byte[] bytes, int offset, boolean littleEndian) {
        if (offset + 4 > bytes.length) {
            return 0;
        }
        long first = bytes[offset] & 0xFFL;
        long second = bytes[offset + 1] & 0xFFL;
        long third = bytes[offset + 2] & 0xFFL;
        long fourth = bytes[offset + 3] & 0xFFL;
        if (littleEndian) {
            return (fourth << 24) | (third << 16) | (second << 8) | first;
        }
        return (first << 24) | (second << 16) | (third << 8) | fourth;
    }

    private ApiException invalidImage() {
        return ApiException.of(ErrorCode.INVALID_IMAGE);
    }
}
