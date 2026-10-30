"use client";

import { ImageOff } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { useI18n } from "@/i18n/provider";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorState } from "@/shared/ui/error-state";

import { CategoryChips } from "./category-chips";
import { TemplateCard } from "./template-card";
import { TemplateGridSkeleton } from "./template-grid-skeleton";
import { useTemplateCategories, useTemplates } from "../queries";

export function TemplatesView({ initialCategory }: { initialCategory: string }) {
  const { t } = useI18n();
  const router = useRouter();
  const pathname = usePathname();
  const [category, setCategory] = useState(initialCategory);
  const categoriesQuery = useTemplateCategories();
  const templatesQuery = useTemplates(category);

  useEffect(() => {
    setCategory(initialCategory);
  }, [initialCategory]);

  function selectCategory(next: string) {
    setCategory(next);
    const href = next === "all" ? pathname : `${pathname}?category=${encodeURIComponent(next)}`;
    router.replace(href, { scroll: false });
  }

  const templates = templatesQuery.data?.items ?? [];

  return (
    <div>
      <div className="flex items-end justify-between gap-8">
        <header className="max-w-[620px]">
          <p className="eyebrow text-accent">{t("templates.eyebrow")}</p>
          <h1 className="display mt-4">{t("templates.title")}</h1>
          <p className="mt-4 max-w-[560px] text-[14px] leading-[1.8] text-ink-secondary">
            {t("templates.description")}
          </p>
        </header>
        {templatesQuery.data ? (
          <div className="hidden shrink-0 items-center gap-4 pb-2 lg:flex">
            <span className="mono text-[12px] text-ink-muted">
              {templatesQuery.data.total === 1
                ? t("templates.totalOne", { count: templatesQuery.data.total })
                : t("templates.total", { count: templatesQuery.data.total })}
            </span>
            <span aria-hidden className="h-px w-16 bg-line-strong" />
          </div>
        ) : null}
      </div>

      <div className="mt-8">
        <CategoryChips
          categories={categoriesQuery.data?.items ?? []}
          selected={category}
          loading={categoriesQuery.isPending}
          onSelect={selectCategory}
        />
      </div>

      <div className="mt-6">
        {templatesQuery.isPending ? <TemplateGridSkeleton /> : null}

        {templatesQuery.isError && !templatesQuery.data ? (
          <ErrorState
            title={t("templates.errorTitle")}
            description={t("templates.errorDescription")}
            retryLabel={t("errors.reload")}
            onRetry={() => void templatesQuery.refetch()}
          />
        ) : null}

        {templatesQuery.data && templates.length === 0 ? (
          <EmptyState
            icon={<ImageOff aria-hidden className="h-5 w-5" />}
            title={t("templates.emptyTitle")}
            description={t("templates.emptyDescription")}
            action={
              category !== "all" ? (
                <Button variant="secondary" onClick={() => selectCategory("all")}>
                  {t("templates.viewAll")}
                </Button>
              ) : undefined
            }
          />
        ) : null}

        {templates.length > 0 ? (
          <div className="grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
            {templates.map((template) => (
              <TemplateCard key={template.id} template={template} fromCategory={category} />
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
}
