import type { Locale } from "./config";
import { enUS } from "./messages/en-US";
import { zhCN, type MessageKey } from "./messages/zh-CN";

const dictionaries: Record<Locale, Record<string, unknown>> = {
  "zh-CN": zhCN as unknown as Record<string, unknown>,
  "en-US": enUS as unknown as Record<string, unknown>
};

const baseDictionary = zhCN as unknown as Record<string, unknown>;

function lookup(source: Record<string, unknown>, key: string): string | undefined {
  const segments = key.split(".");
  let current: unknown = source;
  for (const segment of segments) {
    if (typeof current !== "object" || current === null) {
      return undefined;
    }
    current = (current as Record<string, unknown>)[segment];
  }
  return typeof current === "string" ? current : undefined;
}

function interpolate(template: string, params?: Record<string, string | number>): string {
  if (!params) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match
  );
}

export type TranslateFn = (
  key: MessageKey,
  params?: Record<string, string | number>
) => string;

export function createTranslator(
  sources: Partial<Record<Locale, Record<string, unknown>>>
): (locale: Locale, key: string, params?: Record<string, string | number>) => string {
  return (locale, key, params) => {
    const primary = sources[locale] ?? baseDictionary;
    const value = lookup(primary, key) ?? lookup(baseDictionary, key);
    if (value === undefined) {
      if (process.env.NODE_ENV !== "production") {
        console.warn(`[i18n] Missing message key: ${key} (${locale})`);
      }
      return key;
    }
    return interpolate(value, params);
  };
}

const translate = createTranslator(dictionaries);

export function t(
  locale: Locale,
  key: MessageKey,
  params?: Record<string, string | number>
): string {
  return translate(locale, key, params);
}
