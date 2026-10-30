"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode
} from "react";
import { useQueryClient } from "@tanstack/react-query";

import { DEFAULT_LOCALE, normalizeLocale, type Locale } from "./config";
import { writeLocaleCookie } from "./locale-cookie";
import { t as translate, type TranslateFn } from "./translate";

type LocaleContextValue = {
  locale: Locale;
  setLocale: (next: Locale) => void;
  t: TranslateFn;
};

const LocaleContext = createContext<LocaleContextValue | null>(null);

export function LocaleProvider({
  initialLocale = DEFAULT_LOCALE,
  children
}: {
  initialLocale?: Locale;
  children: ReactNode;
}) {
  const [locale, setLocaleState] = useState<Locale>(() => normalizeLocale(initialLocale));
  const queryClient = useQueryClient();

  const setLocale = useCallback(
    (next: Locale) => {
      const normalized = normalizeLocale(next);
      if (normalized === locale) {
        return;
      }
      writeLocaleCookie(normalized);
      setLocaleState(normalized);
      if (typeof document !== "undefined") {
        document.documentElement.lang = normalized;
      }
      void queryClient.cancelQueries();
      queryClient.clear();
    },
    [locale, queryClient]
  );

  const t = useCallback<TranslateFn>(
    (key, params) => translate(locale, key, params),
    [locale]
  );

  const value = useMemo(() => ({ locale, setLocale, t }), [locale, setLocale, t]);

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useI18n(): LocaleContextValue {
  const context = useContext(LocaleContext);
  if (!context) {
    throw new Error("useI18n must be used within LocaleProvider");
  }
  return context;
}

export function useLocale(): Pick<LocaleContextValue, "locale" | "setLocale"> {
  const { locale, setLocale } = useI18n();
  return { locale, setLocale };
}
