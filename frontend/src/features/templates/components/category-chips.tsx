"use client";

import { useI18n } from "@/i18n/provider";
import { Chip } from "@/shared/ui/chip";
import { Skeleton } from "@/shared/ui/skeleton";

import type { TemplateCategory } from "../types";

export function CategoryChips({
  categories,
  selected,
  loading,
  onSelect
}: {
  categories: TemplateCategory[];
  selected: string;
  loading?: boolean;
  onSelect: (category: string) => void;
}) {
  const { t } = useI18n();

  if (loading) {
    return (
      <div className="flex gap-2" aria-busy aria-label={t("templates.loading")}>
        {Array.from({ length: 5 }).map((_, index) => (
          <Skeleton key={index} className="h-[34px] w-24 rounded-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="relative">
      <div
        role="group"
        aria-label={t("templates.categoriesLabel")}
        className="flex gap-2 overflow-x-auto pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        <Chip selected={selected === "all"} onClick={() => onSelect("all")}>
          {t("templates.all")}
        </Chip>
        {categories.map((category) => (
          <Chip
            key={category.id}
            selected={selected === category.id}
            onClick={() => onSelect(category.id)}
          >
            {category.name}
          </Chip>
        ))}
      </div>
      <span
        aria-hidden
        className="pointer-events-none absolute inset-y-0 right-0 w-10 bg-gradient-to-l from-bg to-transparent"
      />
    </div>
  );
}
