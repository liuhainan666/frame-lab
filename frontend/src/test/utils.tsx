import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderOptions } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";

import { LocaleProvider } from "@/i18n/provider";
import { ToastProvider } from "@/shared/ui/toast";
import type { Locale } from "@/i18n/config";

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false }
    }
  });
}

export function renderWithProviders(
  ui: ReactElement,
  options: RenderOptions & { locale?: Locale; queryClient?: QueryClient } = {}
) {
  const { locale = "zh-CN", queryClient = createTestQueryClient(), ...renderOptions } = options;

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <LocaleProvider initialLocale={locale}>
          <ToastProvider>{children}</ToastProvider>
        </LocaleProvider>
      </QueryClientProvider>
    );
  }

  return {
    queryClient,
    ...render(ui, { wrapper: Wrapper, ...renderOptions })
  };
}
