import { DEFAULT_LOCALE, LOCALE_COOKIE, LOCALE_COOKIE_MAX_AGE_SECONDS, type Locale } from "./config";

export function readLocaleCookie(): Locale | undefined {
  if (typeof document === "undefined") {
    return undefined;
  }
  const entry = document.cookie
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${LOCALE_COOKIE}=`));
  if (!entry) {
    return undefined;
  }
  const value = decodeURIComponent(entry.slice(LOCALE_COOKIE.length + 1));
  if (value === "zh-CN" || value === "en-US") {
    return value;
  }
  return undefined;
}

export function writeLocaleCookie(locale: Locale): void {
  if (typeof document === "undefined") {
    return;
  }
  document.cookie = [
    `${LOCALE_COOKIE}=${encodeURIComponent(locale)}`,
    "Path=/",
    `Max-Age=${LOCALE_COOKIE_MAX_AGE_SECONDS}`,
    "SameSite=Lax"
  ].join("; ");
}

export function clearLocaleCookie(): void {
  if (typeof document === "undefined") {
    return;
  }
  document.cookie = `${LOCALE_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
}

export function currentLocaleOrDefault(): Locale {
  return readLocaleCookie() ?? DEFAULT_LOCALE;
}
