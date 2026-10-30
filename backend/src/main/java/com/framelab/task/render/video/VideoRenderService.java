package com.framelab.task.render.video;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framelab.common.i18n.MessageResolver;
import com.framelab.config.FfmpegProperties;
import com.framelab.task.domain.FailureCode;
import com.framelab.task.domain.TaskFailureException;
import com.framelab.task.domain.TaskStage;
import com.framelab.template.spec.LookSpec;
import com.framelab.template.spec.ResolutionSpec;
import com.framelab.template.spec.VideoRenderSpec;
import com.framelab.template.spec.VideoSlotSpec;
import com.framelab.template.spec.VideoTextSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VideoRenderService {

    private static final Logger log = LoggerFactory.getLogger(VideoRenderService.class);
    private static final String FONT_RESOURCE = "/fonts/Inter-SemiBold.ttf";
    private static final float SLOT_JPEG_QUALITY = 0.95f;
    private static final double MOTION_SCALE = 1.3;

    private final FfmpegProperties properties;
    private final MessageResolver messages;
    private final ObjectMapper objectMapper;
    private final Semaphore slots;

    private volatile Boolean available;
    private volatile Font baseFont;

    public VideoRenderService(FfmpegProperties properties, MessageResolver messages, ObjectMapper objectMapper) {
        this.properties = properties;
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.slots = new Semaphore(Math.max(1, properties.getMaxConcurrent()));
    }

    public boolean isAvailable() {
        if (!properties.isEnabled()) {
            return false;
        }
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (available == null) {
                available = probe(properties.getBinary()) && probe(properties.getProbe());
                if (!available) {
                    log.warn("video.ffmpeg_unavailable binary={} probe={}",
                            properties.getBinary(), properties.getProbe());
                }
            }
            return available;
        }
    }

    public VideoArtifacts render(String taskId, List<BufferedImage> images, VideoRenderSpec spec) {
        if (!isAvailable()) {
            throw failure(FailureCode.VIDEO_UNAVAILABLE, false, "ffmpeg is unavailable");
        }
        if (images.size() != spec.slots().size()) {
            throw failure(FailureCode.VIDEO_INPUT_INVALID, false, "image count does not match slots");
        }
        boolean acquired = false;
        Path work = null;
        try {
            slots.acquire();
            acquired = true;
            work = Files.createTempDirectory("framelab-video-");
            List<Path> slotFiles = writeSlots(work, images, spec);
            Path audio = copyResource(work, "templates/" + spec.audio().path(), "audio.mp3");
            Path lut = spec.look().hasLut()
                    ? copyResource(work, "templates/" + spec.look().lut(), "lut.cube")
                    : null;
            List<Path> titles = paintTitles(work, spec);
            Path watermark = paintWatermark(work, spec, taskId);
            Path video = work.resolve("video.mp4");

            List<String> command = buildCommand(work, slotFiles, audio, titles, watermark, lut, spec);
            run(command, work, properties.getTimeout());

            long size = Files.size(video);
            if (size <= 0 || size > properties.getMaxOutputBytes()) {
                throw failure(FailureCode.VIDEO_OUTPUT_INVALID, true, "video size is invalid");
            }
            verifyOutput(work, video, spec);
            Path poster = extractPoster(work, video);

            return new VideoArtifacts(video, poster, work,
                    spec.resolution().width(), spec.resolution().height(), spec.durationMs());
        } catch (TaskFailureException ex) {
            deleteQuietly(work);
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            deleteQuietly(work);
            throw failure(FailureCode.VIDEO_RENDER_FAILED, true, "video rendering interrupted");
        } catch (IOException ex) {
            deleteQuietly(work);
            throw failure(FailureCode.VIDEO_RENDER_FAILED, true, "video rendering I/O failed");
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    private List<Path> writeSlots(Path work, List<BufferedImage> images, VideoRenderSpec spec) throws IOException {
        List<Path> files = new ArrayList<>(images.size());
        ResolutionSpec resolution = spec.resolution();
        boolean motion = spec.slots().stream().anyMatch(slot -> !"none".equals(slot.motion()));
        int targetWidth = motion ? (int) Math.round(resolution.width() * MOTION_SCALE) : resolution.width();
        int targetHeight = motion ? (int) Math.round(resolution.height() * MOTION_SCALE) : resolution.height();
        for (int index = 0; index < images.size(); index++) {
            BufferedImage prepared = coverCrop(images.get(index), targetWidth, targetHeight);
            Path file = work.resolve("slot-" + (index + 1) + ".jpg");
            writeJpeg(prepared, file);
            files.add(file);
        }
        return files;
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

    private void writeJpeg(BufferedImage image, Path file) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(SLOT_JPEG_QUALITY);
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private Path copyResource(Path work, String resourcePath, String fileName) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw failure(FailureCode.VIDEO_INPUT_INVALID, false, "template resource is missing");
            }
            Path target = work.resolve(fileName);
            Files.copy(input, target);
            return target;
        }
    }

    private List<Path> paintTitles(Path work, VideoRenderSpec spec) throws IOException {
        List<Path> files = new ArrayList<>(spec.text().size());
        for (int index = 0; index < spec.text().size(); index++) {
            VideoTextSpec text = spec.text().get(index);
            BufferedImage overlay = new BufferedImage(spec.resolution().width(), spec.resolution().height(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = overlay.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Font font = font().deriveFont(Font.PLAIN, (float) text.fontSize());
                graphics.setFont(font);
                FontMetrics metrics = graphics.getFontMetrics(font);
                // en_US bundle keeps overlays ASCII; CJK glyphs are absent from the runtime font
                String value = messages.get(Locale.US, text.textKey());
                int x = (int) Math.round(spec.resolution().width() * text.x());
                switch (text.align()) {
                    case "center" -> x -= metrics.stringWidth(value) / 2;
                    case "right" -> x -= metrics.stringWidth(value);
                    default -> {
                    }
                }
                int y = (int) Math.round(spec.resolution().height() * text.y()) + metrics.getAscent();
                int shadow = Math.max(1, text.fontSize() / 32);
                graphics.setColor(new Color(0, 0, 0, 135));
                graphics.drawString(value, x + shadow, y + shadow);
                graphics.setColor(new Color(255, 255, 255, 240));
                graphics.drawString(value, x, y);
            } finally {
                graphics.dispose();
            }
            Path file = work.resolve("title-" + (index + 1) + ".png");
            ImageIO.write(overlay, "png", file.toFile());
            files.add(file);
        }
        return files;
    }

    private Path paintWatermark(Path work, VideoRenderSpec spec, String taskId) throws IOException {
        int width = spec.resolution().width();
        int height = spec.resolution().height();
        BufferedImage overlay = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = overlay.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            float fontSize = Math.max(24f, height * 0.018f);
            Font font = font().deriveFont(Font.PLAIN, fontSize);
            graphics.setFont(font);
            FontMetrics metrics = graphics.getFontMetrics(font);
            String shortId = taskId.substring(0, Math.min(8, taskId.length()));
            String value = "FRAMELAB · " + shortId;
            int padding = Math.max(24, Math.round(width * 0.025f));
            int x = Math.max(padding, width - padding - metrics.stringWidth(value));
            int y = height - padding;
            graphics.setColor(new Color(0, 0, 0, 150));
            graphics.drawString(value, x + 1, y + 1);
            graphics.setColor(new Color(255, 255, 255, 235));
            graphics.drawString(value, x, y);
        } finally {
            graphics.dispose();
        }
        Path file = work.resolve("watermark.png");
        ImageIO.write(overlay, "png", file.toFile());
        return file;
    }

    private List<String> buildCommand(Path work, List<Path> slotFiles, Path audio, List<Path> titles,
                                      Path watermark, Path lut, VideoRenderSpec spec) {
        ResolutionSpec resolution = spec.resolution();
        int width = resolution.width();
        int height = resolution.height();
        List<String> command = new ArrayList<>();
        command.add(properties.getBinary());
        command.add("-y");
        command.add("-hide_banner");
        command.add("-nostdin");
        for (VideoSlotSpec slot : spec.slots()) {
            command.add("-loop");
            command.add("1");
            command.add("-framerate");
            command.add(String.valueOf(spec.fps()));
            command.add("-t");
            command.add(seconds(slot.durationMs()));
            command.add("-i");
            command.add(slotFiles.get(slot.seq() - 1).getFileName().toString());
        }
        command.add("-i");
        command.add(audio.getFileName().toString());
        for (Path title : titles) {
            command.add("-loop");
            command.add("1");
            command.add("-framerate");
            command.add(String.valueOf(spec.fps()));
            command.add("-t");
            command.add(seconds(spec.durationMs()));
            command.add("-i");
            command.add(title.getFileName().toString());
        }
        command.add("-loop");
        command.add("1");
        command.add("-framerate");
        command.add(String.valueOf(spec.fps()));
        command.add("-t");
        command.add(seconds(spec.durationMs()));
        command.add("-i");
        command.add(watermark.getFileName().toString());

        String graph = filterGraph(spec, width, height, lut, titles.size(), spec.slots().size());
        command.add("-filter_complex");
        command.add(graph);
        command.add("-map");
        command.add("[vout]");
        command.add("-map");
        command.add("[aout]");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-crf");
        command.add("20");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-movflags");
        command.add("+faststart");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        command.add("-t");
        command.add(seconds(spec.durationMs()));
        command.add("-f");
        command.add("mp4");
        command.add("video.mp4");
        return command;
    }

    private String filterGraph(VideoRenderSpec spec, int width, int height, Path lut,
                               int titleCount, int slotCount) {
        StringBuilder graph = new StringBuilder();
        for (VideoSlotSpec slot : spec.slots()) {
            if (graph.length() > 0) {
                graph.append(';');
            }
            int input = slot.seq() - 1;
            graph.append('[').append(input).append(":v]");
            if ("none".equals(slot.motion())) {
                graph.append("scale=").append(width).append(':').append(height);
            } else {
                graph.append("zoompan=")
                        .append("z='").append(zoomExpression(slot)).append("':")
                        .append("x='").append(xExpression(slot, spec.fps())).append("':")
                        .append("y='").append(yExpression(slot)).append("':")
                        .append("d=1:s=").append(width).append('x').append(height)
                        .append(":fps=").append(spec.fps());
            }
            graph.append(",setsar=1,settb=AVTB[v").append(input).append(']');
        }

        String current = "v0";
        int offsetMs = 0;
        for (int index = 1; index < slotCount; index++) {
            VideoSlotSpec slot = spec.slots().get(index);
            VideoSlotSpec previous = spec.slots().get(index - 1);
            offsetMs += previous.durationMs() - slot.transitionMs();
            String next = "x" + index;
            if (graph.length() > 0) {
                graph.append(';');
            }
            graph.append('[').append(current).append("][v").append(index).append("]xfade=transition=")
                    .append(xfadeName(slot.transition()))
                    .append(":duration=").append(seconds(slot.transitionMs()))
                    .append(":offset=").append(seconds(offsetMs))
                    .append('[').append(next).append(']');
            current = next;
        }

        LookSpec look = spec.look();
        if (look.hasLut() && lut != null) {
            graph.append(';').append('[').append(current).append("]split=2[lutA][lutB];")
                    .append("[lutB]lut3d=file='").append(lut.getFileName()).append("':interp=trilinear[lutC];")
                    .append("[lutA][lutC]blend=all_mode=normal:all_opacity=")
                    .append(decimal(Math.min(1, look.lutStrength()))).append("[lk]");
        } else {
            graph.append(';').append('[').append(current).append("]null[lk]");
        }

        StringBuilder filters = new StringBuilder();
        filters.append("eq=brightness=").append(decimal(look.exposure() * 0.18))
                .append(":contrast=").append(decimal(look.contrast()))
                .append(":saturation=").append(decimal(look.saturation()));
        if (look.temperature() != 0) {
            float shift = (float) (look.temperature() / 100 * 0.12);
            filters.append(",colorbalance=rs=").append(decimal(shift))
                    .append(":bs=").append(decimal(-shift));
        }
        if (look.vignette() > 0) {
            filters.append(",vignette=angle=").append(decimal(0.3 + look.vignette() * 1.5));
        }
        if (look.grain() > 0) {
            filters.append(",noise=alls=").append(Math.max(1, Math.round(look.grain() * 40)))
                    .append(":allf=t+u");
        }
        filters.append(",format=yuv420p");
        graph.append(';').append("[lk]").append(filters).append("[vbase]");

        int watermarkInput = slotCount + 1 + titleCount;
        graph.append(';').append("[vbase][").append(watermarkInput).append(":v]overlay=0:0[vwm]");
        String last = "vwm";
        for (int index = 0; index < titleCount; index++) {
            VideoTextSpec text = spec.text().get(index);
            String next = "vt" + index;
            graph.append(';').append('[').append(last).append("][").append(slotCount + 1 + index)
                    .append(":v]overlay=0:0:enable='between(t,")
                    .append(seconds(text.startMs())).append(',').append(seconds(text.endMs()))
                    .append(")'[").append(next).append(']');
            last = next;
        }
        graph.append(';').append('[').append(last).append("]null[vout]");

        graph.append(';').append('[').append(slotCount).append(":a]atrim=0:")
                .append(seconds(spec.durationMs()))
                .append(",volume=").append(decimal(spec.audio().volume()))
                .append(",afade=t=out:st=")
                .append(seconds(Math.max(0, spec.durationMs() - spec.audio().fadeOutMs())))
                .append(":d=").append(seconds(spec.audio().fadeOutMs()))
                .append(",asetpts=PTS-STARTPTS[aout]");
        return graph.toString();
    }

    private String zoomExpression(VideoSlotSpec slot) {
        return switch (slot.motion()) {
            case "zoom-in" -> "min(1+0.0009*on,1.15)";
            case "zoom-out" -> "max(1.15-0.0009*on,1.0)";
            case "pan-left", "pan-right" -> "1.08";
            default -> "1";
        };
    }

    private String xExpression(VideoSlotSpec slot, int fps) {
        int frames = Math.max(1, Math.round(slot.durationMs() / 1000f * fps));
        return switch (slot.motion()) {
            case "pan-left" -> "(iw-iw/zoom)*(1-on/" + frames + ")";
            case "pan-right" -> "(iw-iw/zoom)*on/" + frames;
            default -> "iw/2-(iw/zoom/2)";
        };
    }

    private String yExpression(VideoSlotSpec slot) {
        return "ih/2-(ih/zoom/2)";
    }

    private String xfadeName(String transition) {
        return switch (transition) {
            case "slide-left" -> "slideleft";
            case "slide-right" -> "slideright";
            case "zoom" -> "zoomin";
            default -> "fade";
        };
    }

    private void run(List<String> command, Path work, Duration timeout) throws IOException, InterruptedException {
        Path logFile = work.resolve("ffmpeg.log");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(work.toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        builder.redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        Process process = builder.start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            throw failure(FailureCode.VIDEO_TIMEOUT, true, "ffmpeg timed out");
        }
        if (process.exitValue() != 0) {
            log.error("video.ffmpeg_failed exit={} log={}", process.exitValue(), tail(logFile, 2000));
            throw failure(FailureCode.VIDEO_RENDER_FAILED, true, "ffmpeg exited with an error");
        }
    }

    private void verifyOutput(Path work, Path video, VideoRenderSpec spec) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(properties.getProbe(),
                "-v", "error", "-print_format", "json", "-show_format", "-show_streams",
                video.getFileName().toString());
        builder.directory(work.toFile());
        builder.redirectErrorStream(false);
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0) {
            process.destroyForcibly();
            throw failure(FailureCode.VIDEO_OUTPUT_INVALID, true, "ffprobe failed");
        }
        JsonNode root = objectMapper.readTree(output);
        boolean videoOk = false;
        boolean audioOk = false;
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asText())) {
                videoOk = "h264".equals(stream.path("codec_name").asText())
                        && stream.path("width").asInt() == spec.resolution().width()
                        && stream.path("height").asInt() == spec.resolution().height();
            } else if ("audio".equals(stream.path("codec_type").asText())) {
                audioOk = "aac".equals(stream.path("codec_name").asText());
            }
        }
        double durationSeconds = root.path("format").path("duration").asDouble(-1);
        boolean durationOk = durationSeconds > 0
                && Math.abs(durationSeconds * 1000 - spec.durationMs()) <= 250;
        if (!videoOk || !audioOk || !durationOk) {
            log.error("video.output_invalid video={} audio={} duration={} expected={}",
                    videoOk, audioOk, durationSeconds, spec.durationMs());
            throw failure(FailureCode.VIDEO_OUTPUT_INVALID, true, "generated video failed validation");
        }
    }

    private Path extractPoster(Path work, Path video) throws IOException, InterruptedException {
        Path poster = work.resolve("poster.jpg");
        List<String> command = List.of(properties.getBinary(), "-y", "-v", "error",
                "-ss", "0.5", "-i", video.getFileName().toString(),
                "-frames:v", "1", "-q:v", "2", poster.getFileName().toString());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(work.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        process.getInputStream().readAllBytes();
        if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0
                || !Files.isRegularFile(poster) || Files.size(poster) == 0) {
            process.destroyForcibly();
            throw failure(FailureCode.VIDEO_OUTPUT_INVALID, true, "poster extraction failed");
        }
        return poster;
    }

    private boolean probe(String binary) {
        try {
            ProcessBuilder builder = new ProcessBuilder(binary, "-version");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            process.getInputStream().readAllBytes();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private TaskFailureException failure(FailureCode code, boolean retryable, String detail) {
        return new TaskFailureException(TaskStage.FINALIZE, code, false, retryable, detail);
    }

    private Font font() {
        Font font = baseFont;
        if (font == null) {
            synchronized (this) {
                font = baseFont;
                if (font == null) {
                    try (InputStream input = VideoRenderService.class.getResourceAsStream(FONT_RESOURCE)) {
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

    private static String seconds(int millis) {
        return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String tail(Path file, int maxBytes) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int from = Math.max(0, bytes.length - maxBytes);
            return new String(bytes, from, bytes.length - from, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort cleanup of a temporary render directory.
                }
            });
        } catch (IOException ignored) {
            // The original failure is more useful than cleanup noise.
        }
    }

    public record VideoArtifacts(Path videoPath, Path posterPath, Path workDir,
                                 int width, int height, int durationMs) implements AutoCloseable {

        @Override
        public void close() {
            deleteQuietly(workDir);
        }
    }
}
