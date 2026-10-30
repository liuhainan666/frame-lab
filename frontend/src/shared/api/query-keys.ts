import type { Locale } from "@/i18n/config";

export type TaskScope = "all" | "active" | "succeeded" | "failed";

export type TaskFilters = {
  scope?: TaskScope;
  templateId?: string;
  page?: number;
  size?: number;
};

export const queryKeys = {
  templates: (locale: Locale, category: string = "all") =>
    ["templates", locale, category] as const,
  template: (locale: Locale, templateId: string) => ["template", locale, templateId] as const,
  templateCategories: (locale: Locale) => ["template-categories", locale] as const,
  tasks: (userId: number, locale: Locale, filters: TaskFilters) =>
    ["tasks", userId, locale, filters] as const,
  task: (userId: number, locale: Locale, taskId: string) =>
    ["task", userId, locale, taskId] as const,
  assetImage: (userId: number, assetId: string) => ["asset-image", userId, assetId] as const,
  resultImage: (userId: number, taskId: string) => ["result-image", userId, taskId] as const
};
