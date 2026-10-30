"use client";

import { useI18n } from "@/i18n/provider";
import { cn } from "@/shared/lib/cn";

export function LocaleSwitcher({ compact = false }: { compact?: boolean }) {
  const { locale, setLocale, t } = useI18n();
  return (
    <div
      role="group"
      aria-label={t("language.label")}
      className="flex h-9 items-center rounded-full border border-line bg-bg-elevated/80 p-0.5"
    >
      {[
        { value: "zh-CN" as const, label: compact ? "中" : t("language.zh") },
        { value: "en-US" as const, label: compact ? "EN" : t("language.en") }
      ].map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={locale === option.value}
          onClick={() => setLocale(option.value)}
          className={cn(
            "h-8 rounded-full px-3 text-[11px] font-medium transition-colors",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent",
            locale === option.value
              ? "bg-accent text-accent-ink"
              : "text-ink-muted hover:text-ink"
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
