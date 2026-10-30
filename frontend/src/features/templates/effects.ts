import type { TranslateFn } from "@/i18n/translate";
import type { MessageKey } from "@/i18n/messages/zh-CN";

const effectKeys: Record<string, MessageKey> = {
  travel: "templates.effects.travel",
  warm: "templates.effects.warm",
  film: "templates.effects.film",
  clear: "templates.effects.clear",
  cool: "templates.effects.cool",
  festival: "templates.effects.festival",
  bright: "templates.effects.bright",
  portrait: "templates.effects.portrait",
  soft: "templates.effects.soft",
  clean: "templates.effects.clean",
  rich: "templates.effects.rich",
  cinematic: "templates.effects.cinematic",
  golden: "templates.effects.golden"
};

export function templateEffectLabel(tag: string, t: TranslateFn): string {
  const key = effectKeys[tag];
  return key ? t(key) : tag;
}

export function templateDurationSeconds(durationMs: number | null | undefined): number | null {
  if (!durationMs || durationMs <= 0) {
    return null;
  }
  return Math.round(durationMs / 1000);
}
