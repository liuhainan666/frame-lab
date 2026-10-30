"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

import { AuthBootstrap } from "@/features/auth/components/auth-bootstrap";
import { LocaleProvider } from "@/i18n/provider";
import type { Locale } from "@/i18n/config";
import { isApiError } from "@/shared/api/errors";
import { ToastProvider } from "@/shared/ui/toast";

export function Providers({
  initialLocale,
  children
}: {
  initialLocale: Locale;
  children: ReactNode;
}) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 0,
            refetchOnWindowFocus: false,
            retry: (failureCount, error) => {
              if (isApiError(error) && error.status > 0 && error.status < 500) {
                return false;
              }
              return failureCount < 1;
            }
          }
        }
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <LocaleProvider initialLocale={initialLocale}>
        <ToastProvider>
          <AuthBootstrap />
          {children}
        </ToastProvider>
      </LocaleProvider>
    </QueryClientProvider>
  );
}
