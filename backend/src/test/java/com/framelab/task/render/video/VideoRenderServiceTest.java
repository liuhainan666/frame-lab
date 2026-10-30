package com.framelab.task.render.video;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framelab.common.i18n.MessageResolver;
import com.framelab.config.FfmpegProperties;
import com.framelab.template.TemplateKind;
import com.framelab.template.spec.LookSpec;
import com.framelab.template.spec.ResolutionSpec;
import com.framelab.template.spec.VideoAudioSpec;
import com.framelab.template.spec.VideoRenderSpec;
import com.framelab.template.spec.VideoSlotSpec;
import com.framelab.template.spec.VideoTextSpec;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VideoRenderServiceTest {

    @Test
    void rendersMp4AndPosterWhenFfmpegIsAvailable() throws Exception {
        FfmpegProperties properties = new FfmpegProperties();
        properties.setMaxConcurrent(1);
        VideoRenderService service = new VideoRenderService(properties,
                new MessageResolver(new StaticMessageSource()), new ObjectMapper());
        assumeTrue(service.isAvailable(), "ffmpeg is required for video rendering");

        VideoRenderSpec spec = new VideoRenderSpec(1, TemplateKind.VIDEO, 3000, 24,
                new ResolutionSpec(360, 640),
                List.of(
                        new VideoSlotSpec(1, 1200, "fade", 300, "zoom-in"),
                        new VideoSlotSpec(2, 1200, "slide-left", 300, "pan-right"),
                        new VideoSlotSpec(3, 1200, "fade", 300, "none")),
                new LookSpec(null, null, null, null, null, null, null, null, null, null,
                        0.1, 0.02, null, null),
                new VideoAudioSpec("audio/city-walk-v1.mp3", 0.5, 300),
                List.of(), true);

        VideoRenderService.VideoArtifacts artifacts;
        try (VideoRenderService.VideoArtifacts rendered = service.render("task-video-01",
                List.of(image(Color.RED), image(Color.GREEN), image(Color.BLUE)), spec)) {
            artifacts = rendered;
            assertThat(Files.size(rendered.videoPath())).isGreaterThan(0);
            assertThat(Files.size(rendered.posterPath())).isGreaterThan(0);
            assertThat(rendered.width()).isEqualTo(360);
            assertThat(rendered.height()).isEqualTo(640);
            assertThat(rendered.durationMs()).isEqualTo(3000);
        }
        assertThat(Files.exists(artifacts.workDir())).isFalse();
    }

    @Test
    void rendersLutAndTextOverlay() throws Exception {
        FfmpegProperties properties = new FfmpegProperties();
        VideoRenderService service = new VideoRenderService(properties,
                new MessageResolver(new StaticMessageSource()) {
                    @Override
                    public String get(Locale locale, String key, Object... args) {
                        return "City Walk";
                    }
                }, new ObjectMapper());
        assumeTrue(service.isAvailable(), "ffmpeg is required for video rendering");

        VideoRenderSpec spec = new VideoRenderSpec(1, TemplateKind.VIDEO, 3000, 24,
                new ResolutionSpec(360, 640),
                List.of(
                        new VideoSlotSpec(1, 1200, "fade", 300, "zoom-in"),
                        new VideoSlotSpec(2, 1200, "zoom", 300, "zoom-out"),
                        new VideoSlotSpec(3, 1200, "slide-right", 300, "none")),
                new LookSpec("city-warm", "luts/city-warm-v1.cube", 0.8, 0.04, 1.04, 1.02,
                        4.0, 0.0, 0.0, 0.0, 0.12, 0.04, 0.0, 0.0),
                new VideoAudioSpec("audio/city-walk-v1.mp3", 0.8, 300),
                List.of(new VideoTextSpec("video.cityWalk.title", 100, 1500,
                        0.1, 0.1, 32, "#FFFFFF", "left")),
                true);

        try (VideoRenderService.VideoArtifacts rendered = service.render("task-video-02",
                List.of(image(Color.RED), image(Color.GREEN), image(Color.BLUE)), spec)) {
            assertThat(Files.size(rendered.videoPath())).isGreaterThan(0);
        }
    }

    private BufferedImage image(Color color) {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
