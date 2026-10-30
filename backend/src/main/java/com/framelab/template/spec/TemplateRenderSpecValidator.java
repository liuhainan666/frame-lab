package com.framelab.template.spec;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.framelab.common.i18n.MessageResolver;
import com.framelab.template.TemplateKind;
import org.springframework.stereotype.Component;

@Component
public class TemplateRenderSpecValidator {

    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final Pattern TEXT_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9.]{2,79}");
    private static final Set<String> TRANSITIONS = Set.of("fade", "slide-left", "slide-right", "zoom");
    private static final Set<String> MOTIONS = Set.of("zoom-in", "zoom-out", "pan-left", "pan-right", "none");
    private static final Set<String> ALIGNS = Set.of("left", "center", "right");

    private final TemplateRenderSpecParser parser;
    private final MessageResolver messages;

    public TemplateRenderSpecValidator(TemplateRenderSpecParser parser, MessageResolver messages) {
        this.parser = parser;
        this.messages = messages;
    }

    public TemplateRenderSpec validate(String json, TemplateKind expectedKind) {
        TemplateRenderSpec spec = parser.parse(json);
        if (spec.version() != 1) {
            throw new TemplateSpecException("unsupported spec version");
        }
        if (spec.kind() != expectedKind) {
            throw new TemplateSpecException("spec kind does not match template kind");
        }
        if (spec instanceof ImageRenderSpec image) {
            validateImage(image);
        } else if (spec instanceof VideoRenderSpec video) {
            validateVideo(video);
        } else {
            throw new TemplateSpecException("unsupported spec type");
        }
        return spec;
    }

    private void validateImage(ImageRenderSpec spec) {
        if (spec.look() == null) {
            throw new TemplateSpecException("image look is required");
        }
        validateLook(spec.look());
        if (spec.portrait() == null) {
            throw new TemplateSpecException("image portrait is required");
        }
        PortraitSpec portrait = spec.portrait();
        if (portrait.skinSmooth() < 0 || portrait.skinSmooth() > 0.5) {
            throw new TemplateSpecException("skinSmooth out of range");
        }
        if (portrait.skinBrighten() < 0 || portrait.skinBrighten() > 0.2) {
            throw new TemplateSpecException("skinBrighten out of range");
        }
        if (portrait.warmth() < 0 || portrait.warmth() > 0.2) {
            throw new TemplateSpecException("portrait warmth out of range");
        }
        if (spec.layout() == null) {
            throw new TemplateSpecException("image layout is required");
        }
        if (!"legacy".equals(spec.layout().engine())) {
            throw new TemplateSpecException("unsupported layout engine");
        }
        if (spec.layout().gap() < 0 || spec.layout().gap() > 64) {
            throw new TemplateSpecException("layout gap out of range");
        }
        if (spec.overlay() != null) {
            requireResource(spec.overlay().path(), "overlays/", ".png");
            if (spec.overlay().opacity() < 0 || spec.overlay().opacity() > 1) {
                throw new TemplateSpecException("overlay opacity out of range");
            }
        }
    }

    private void validateVideo(VideoRenderSpec spec) {
        ResolutionSpec resolution = spec.resolution();
        if (resolution == null) {
            throw new TemplateSpecException("video resolution is required");
        }
        if (resolution.width() < 240 || resolution.width() > 1080
                || resolution.height() < 240 || resolution.height() > 1920) {
            throw new TemplateSpecException("video resolution out of range");
        }
        if (spec.fps() < 24 || spec.fps() > 30) {
            throw new TemplateSpecException("video fps out of range");
        }
        List<VideoSlotSpec> slots = spec.slots();
        if (slots.size() < 3 || slots.size() > 5) {
            throw new TemplateSpecException("video slot count must be 3..5");
        }
        for (int index = 0; index < slots.size(); index++) {
            VideoSlotSpec slot = slots.get(index);
            if (slot.seq() != index + 1) {
                throw new TemplateSpecException("video slot seq is not sequential");
            }
            if (slot.durationMs() < 500 || slot.durationMs() > 15000) {
                throw new TemplateSpecException("video slot duration out of range");
            }
            if (!MOTIONS.contains(slot.motion())) {
                throw new TemplateSpecException("unsupported slot motion");
            }
            if (index > 0) {
                if (!TRANSITIONS.contains(slot.transition())) {
                    throw new TemplateSpecException("unsupported transition");
                }
                if (slot.transitionMs() < 300 || slot.transitionMs() > 800) {
                    throw new TemplateSpecException("transition duration out of range");
                }
                int shorter = Math.min(slot.durationMs(), slots.get(index - 1).durationMs());
                if (slot.transitionMs() >= shorter) {
                    throw new TemplateSpecException("transition is longer than adjacent slots");
                }
            }
        }
        int contentDuration = spec.contentDurationMs();
        if (contentDuration != spec.durationMs()) {
            throw new TemplateSpecException("video duration does not match slots");
        }
        if (contentDuration < 3000 || contentDuration > 60000) {
            throw new TemplateSpecException("video duration out of range");
        }
        if (spec.look() == null) {
            throw new TemplateSpecException("video look is required");
        }
        validateLook(spec.look());
        if (spec.audio() == null) {
            throw new TemplateSpecException("video audio is required");
        }
        requireResource(spec.audio().path(), "audio/", ".mp3");
        if (spec.audio().volume() < 0 || spec.audio().volume() > 1) {
            throw new TemplateSpecException("audio volume out of range");
        }
        if (spec.audio().fadeOutMs() < 0 || spec.audio().fadeOutMs() > 2000) {
            throw new TemplateSpecException("audio fadeOutMs out of range");
        }
        if (spec.text().size() > 3) {
            throw new TemplateSpecException("video text count exceeds 3");
        }
        for (VideoTextSpec text : spec.text()) {
            validateText(text, contentDuration);
        }
    }

    private void validateText(VideoTextSpec text, int durationMs) {
        if (text.textKey() == null || !TEXT_KEY.matcher(text.textKey()).matches()
                || !messages.exists(text.textKey())) {
            throw new TemplateSpecException("video text key is missing");
        }
        if (text.startMs() < 0 || text.endMs() <= text.startMs() || text.endMs() > durationMs) {
            throw new TemplateSpecException("video text timing out of range");
        }
        if (text.x() < 0 || text.x() > 1 || text.y() < 0 || text.y() > 1) {
            throw new TemplateSpecException("video text position out of range");
        }
        if (text.fontSize() < 12 || text.fontSize() > 120) {
            throw new TemplateSpecException("video text fontSize out of range");
        }
        if (text.color() == null || !COLOR.matcher(text.color()).matches()) {
            throw new TemplateSpecException("video text color is invalid");
        }
        if (!ALIGNS.contains(text.align())) {
            throw new TemplateSpecException("video text align is invalid");
        }
    }

    private void validateLook(LookSpec look) {
        if (look.preset() != null && (look.preset().isBlank() || look.preset().length() > 40)) {
            throw new TemplateSpecException("look preset is invalid");
        }
        if (look.lut() != null) {
            requireResource(look.lut(), "luts/", ".cube");
        }
        requireRange(look.lutStrength(), 0, 1, "lutStrength");
        requireRange(look.exposure(), -2, 2, "exposure");
        requireRange(look.contrast(), 0.5, 2, "contrast");
        requireRange(look.saturation(), 0, 2, "saturation");
        requireRange(look.temperature(), -50, 50, "temperature");
        requireRange(look.tint(), -50, 50, "tint");
        requireRange(look.highlights(), -100, 100, "highlights");
        requireRange(look.shadows(), -100, 100, "shadows");
        requireRange(look.vignette(), 0, 1, "vignette");
        requireRange(look.grain(), 0, 1, "grain");
        requireRange(look.bloom(), 0, 1, "bloom");
        requireRange(look.clarity(), 0, 1, "clarity");
    }

    private void requireRange(double value, double min, double max, String field) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new TemplateSpecException(field + " out of range");
        }
    }

    private void requireResource(String value, String prefix, String extension) {
        if (value == null || value.isBlank() || value.length() > 120
                || !value.startsWith(prefix) || !value.endsWith(extension)
                || value.startsWith("/") || value.contains("..") || value.contains("\\")
                || value.contains("//") || value.contains(":")) {
            throw new TemplateSpecException("resource key is invalid");
        }
    }
}
