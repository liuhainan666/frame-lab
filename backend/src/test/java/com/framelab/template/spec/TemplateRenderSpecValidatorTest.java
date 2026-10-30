package com.framelab.template.spec;

import com.framelab.common.i18n.MessageResolver;
import com.framelab.template.TemplateKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRenderSpecValidatorTest {

    private static final String IMAGE_SPEC = """
            {
              "version": 1,
              "kind": "IMAGE",
              "look": {
                "preset": "film-warm",
                "lut": "luts/film-warm-v1.cube",
                "lutStrength": 0.85,
                "exposure": 0.08,
                "contrast": 1.06,
                "saturation": 0.96,
                "temperature": 6,
                "tint": -2,
                "highlights": -10,
                "shadows": 8,
                "vignette": 0.16,
                "grain": 0.05,
                "bloom": 0.06
              },
              "portrait": { "enabled": false, "skinSmooth": 0.0, "skinBrighten": 0.0, "warmth": 0.0 },
              "layout": { "engine": "legacy", "gap": 12 },
              "overlay": null,
              "watermark": true
            }
            """;

    private static final String VIDEO_SPEC = """
            {
              "version": 1,
              "kind": "VIDEO",
              "durationMs": 15000,
              "fps": 30,
              "resolution": { "width": 1080, "height": 1920 },
              "slots": [
                { "seq": 1, "durationMs": 3400, "transition": "fade", "transitionMs": 500, "motion": "zoom-in" },
                { "seq": 2, "durationMs": 3400, "transition": "slide-left", "transitionMs": 500, "motion": "pan-right" },
                { "seq": 3, "durationMs": 3400, "transition": "fade", "transitionMs": 500, "motion": "zoom-out" },
                { "seq": 4, "durationMs": 3400, "transition": "slide-left", "transitionMs": 500, "motion": "zoom-in" },
                { "seq": 5, "durationMs": 3400, "transition": "fade", "transitionMs": 500, "motion": "none" }
              ],
              "look": {
                "preset": "city-warm",
                "lut": "luts/city-warm-v1.cube",
                "lutStrength": 0.8,
                "exposure": 0.04,
                "contrast": 1.04,
                "saturation": 1.02,
                "temperature": 4,
                "vignette": 0.12,
                "grain": 0.04
              },
              "audio": { "path": "audio/city-walk-v1.mp3", "volume": 0.8, "fadeOutMs": 800 },
              "text": [
                {
                  "textKey": "video.cityWalk.title",
                  "startMs": 500,
                  "endMs": 4000,
                  "x": 0.08,
                  "y": 0.12,
                  "fontSize": 56,
                  "color": "#FFFFFF",
                  "align": "left"
                }
              ],
              "watermark": true
            }
            """;

    private TemplateRenderSpecValidator validator;

    @BeforeEach
    void setUp() {
        MessageResolver messages = new MessageResolver(new StaticMessageSource()) {
            @Override
            public boolean exists(String key) {
                return "video.cityWalk.title".equals(key);
            }
        };
        validator = new TemplateRenderSpecValidator(new TemplateRenderSpecParser(), messages);
    }

    @Test
    void acceptsImageSpec() {
        TemplateRenderSpec spec = validator.validate(IMAGE_SPEC, TemplateKind.IMAGE);

        assertThat(spec).isInstanceOf(ImageRenderSpec.class);
        ImageRenderSpec image = (ImageRenderSpec) spec;
        assertThat(image.look().hasLut()).isTrue();
        assertThat(image.layout().engine()).isEqualTo("legacy");
    }

    @Test
    void rejectsUnknownFields() {
        String json = IMAGE_SPEC.replace("\"watermark\": true", "\"watermark\": true, \"extra\": 1");

        assertThatThrownBy(() -> validator.validate(json, TemplateKind.IMAGE))
                .isInstanceOf(TemplateSpecException.class);
    }

    @Test
    void rejectsResourceTraversal() {
        String json = IMAGE_SPEC.replace("luts/film-warm-v1.cube", "luts/../secret.cube");

        assertThatThrownBy(() -> validator.validate(json, TemplateKind.IMAGE))
                .isInstanceOf(TemplateSpecException.class);
    }

    @Test
    void acceptsVideoSpecAndComputesDuration() {
        VideoRenderSpec video = (VideoRenderSpec) validator.validate(VIDEO_SPEC, TemplateKind.VIDEO);

        assertThat(video.contentDurationMs()).isEqualTo(15000);
        assertThat(video.transitionCount()).isEqualTo(4);
        assertThat(video.text()).hasSize(1);
    }

    @Test
    void rejectsVideoDurationMismatch() {
        String json = VIDEO_SPEC.replace("\"durationMs\": 15000", "\"durationMs\": 14000");

        assertThatThrownBy(() -> validator.validate(json, TemplateKind.VIDEO))
                .isInstanceOf(TemplateSpecException.class)
                .hasMessageContaining("duration");
    }

    @Test
    void rejectsMissingTextKey() {
        String json = VIDEO_SPEC.replace("video.cityWalk.title", "video.missing.title");

        assertThatThrownBy(() -> validator.validate(json, TemplateKind.VIDEO))
                .isInstanceOf(TemplateSpecException.class)
                .hasMessageContaining("text key");
    }

    @Test
    void rejectsKindMismatch() {
        assertThatThrownBy(() -> validator.validate(IMAGE_SPEC, TemplateKind.VIDEO))
                .isInstanceOf(TemplateSpecException.class)
                .hasMessageContaining("kind");
    }
}
