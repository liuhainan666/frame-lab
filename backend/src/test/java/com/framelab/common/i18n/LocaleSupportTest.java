package com.framelab.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocaleSupportTest {

    @Test
    void resolvesSupportedLanguagesAndWeights() {
        assertThat(LocaleSupport.resolve("en-US,en;q=0.9,zh-CN;q=0.8")).isEqualTo(LocaleSupport.EN_US);
        assertThat(LocaleSupport.resolve("en")).isEqualTo(LocaleSupport.EN_US);
        assertThat(LocaleSupport.resolve("zh-CN")).isEqualTo(LocaleSupport.ZH_CN);
        assertThat(LocaleSupport.resolve("zh-TW")).isEqualTo(LocaleSupport.ZH_CN);
        assertThat(LocaleSupport.resolve("en;q=0.1,zh-CN;q=0.9")).isEqualTo(LocaleSupport.ZH_CN);
    }

    @Test
    void fallsBackToChineseForUnsupportedOrMissingHeaders() {
        assertThat(LocaleSupport.resolve(null)).isEqualTo(LocaleSupport.ZH_CN);
        assertThat(LocaleSupport.resolve("")).isEqualTo(LocaleSupport.ZH_CN);
        assertThat(LocaleSupport.resolve("fr-FR,fr;q=0.9")).isEqualTo(LocaleSupport.ZH_CN);
        assertThat(LocaleSupport.resolve("*")).isEqualTo(LocaleSupport.ZH_CN);
        assertThat(LocaleSupport.resolve("fr,en;q=0.5")).isEqualTo(LocaleSupport.EN_US);
        assertThat(LocaleSupport.resolve("en;q=0,zh-CN;q=0.5")).isEqualTo(LocaleSupport.ZH_CN);
    }

    @Test
    void returnsStableLanguageTags() {
        assertThat(LocaleSupport.tag(LocaleSupport.ZH_CN)).isEqualTo("zh-CN");
        assertThat(LocaleSupport.tag(LocaleSupport.EN_US)).isEqualTo("en-US");
    }
}
