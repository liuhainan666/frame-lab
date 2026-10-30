package com.framelab.common.i18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LocaleSupport {

    public static final Locale ZH_CN = Locale.forLanguageTag("zh-CN");
    public static final Locale EN_US = Locale.forLanguageTag("en-US");
    public static final String ZH_CN_TAG = "zh-CN";
    public static final String EN_US_TAG = "en-US";

    private LocaleSupport() {
    }

    public static Locale resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return ZH_CN;
        }
        List<Candidate> candidates = new ArrayList<>();
        int order = 0;
        for (String part : acceptLanguage.split(",")) {
            String[] pieces = part.trim().split(";");
            if (pieces.length == 0 || pieces[0].isBlank()) {
                continue;
            }
            String tag = pieces[0].trim();
            double quality = 1.0d;
            for (int i = 1; i < pieces.length; i++) {
                String parameter = pieces[i].trim();
                if (parameter.regionMatches(true, 0, "q=", 0, 2)) {
                    try {
                        quality = Double.parseDouble(parameter.substring(2).trim());
                    } catch (NumberFormatException ignored) {
                        quality = 0.0d;
                    }
                    quality = Math.max(0.0d, Math.min(1.0d, quality));
                }
            }
            candidates.add(new Candidate(tag, quality, order++));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::quality).reversed()
                .thenComparingInt(Candidate::order));
        for (Candidate candidate : candidates) {
            if (candidate.quality() <= 0.0d) {
                continue;
            }
            String tag = candidate.tag().toLowerCase(Locale.ROOT);
            if (tag.equals("*")) {
                continue;
            }
            if (tag.equals("en") || tag.startsWith("en-")) {
                return EN_US;
            }
            if (tag.equals("zh") || tag.startsWith("zh-")) {
                return ZH_CN;
            }
        }
        return ZH_CN;
    }

    public static String tag(Locale locale) {
        if (locale != null && "en".equals(locale.getLanguage())) {
            return EN_US_TAG;
        }
        return ZH_CN_TAG;
    }

    private record Candidate(String tag, double quality, int order) {
    }
}
