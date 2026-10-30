"use client";

import { useQuery } from "@tanstack/react-query";

import { useI18n } from "@/i18n/provider";
import { queryKeys } from "@/shared/api/query-keys";

import { fetchCategories, fetchTemplate, fetchTemplates } from "./api";

const TEMPLATE_STALE_TIME = 5 * 60 * 1000;

export function useTemplateCategories() {
  const { locale } = useI18n();
  return useQuery({
    queryKey: queryKeys.templateCategories(locale),
    queryFn: ({ signal }) => fetchCategories(signal),
    staleTime: TEMPLATE_STALE_TIME
  });
}

export function useTemplates(category: string) {
  const { locale } = useI18n();
  return useQuery({
    queryKey: queryKeys.templates(locale, category),
    queryFn: ({ signal }) => fetchTemplates(category, signal),
    staleTime: TEMPLATE_STALE_TIME
  });
}

export function useTemplate(templateId: string) {
  const { locale } = useI18n();
  return useQuery({
    queryKey: queryKeys.template(locale, templateId),
    queryFn: ({ signal }) => fetchTemplate(templateId, signal),
    staleTime: TEMPLATE_STALE_TIME,
    placeholderData: (previous) => previous,
    retry: (failureCount, error) => {
      if (typeof error === "object" && error !== null && "status" in error) {
        const status = (error as { status?: number }).status;
        if (status === 404) {
          return false;
        }
      }
      return failureCount < 1;
    }
  });
}
