import { apiFetch } from "@/shared/api/client";

import type {
  CategoryListResponse,
  TemplateDetail,
  TemplateListResponse
} from "./types";

export function fetchCategories(signal?: AbortSignal): Promise<CategoryListResponse> {
  return apiFetch<CategoryListResponse>("/api/template-categories", { signal });
}

export function fetchTemplates(
  category: string,
  signal?: AbortSignal
): Promise<TemplateListResponse> {
  const query = category && category !== "all" ? `?category=${encodeURIComponent(category)}` : "";
  return apiFetch<TemplateListResponse>(`/api/templates${query}`, { signal });
}

export function fetchTemplate(templateId: string, signal?: AbortSignal): Promise<TemplateDetail> {
  return apiFetch<TemplateDetail>(`/api/templates/${encodeURIComponent(templateId)}`, { signal });
}
