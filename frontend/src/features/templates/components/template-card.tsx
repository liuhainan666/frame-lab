"use client";

import { ArrowUpRight } from "lucide-react";
import Link from "next/link";

import { useI18n } from "@/i18n/provider";
import { backendUrl } from "@/shared/api/client";

import type { TemplateSummary } from "../types";

export function TemplateCard({
  template,
  fromCategory
}: {
  template: TemplateSummary;
  fromCategory: string;
}) {
  const { t } = useI18n();
  return (
    <Link
      href={`/templates/${template.id}?fromCategory=${encodeURIComponent(fromCategory)}`}
      data-testid="template-card"
      className="group relative block aspect-[3/4] overflow-hidden rounded-[var(--radius-lg)] border border-line bg-surface shadow-[var(--shadow-card)] transition-[transform,border-color,box-shadow] duration-300 hover:-translate-y-0.5 hover:border-accent-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg"
    >
      <img
        src={backendUrl(template.coverUrl)}
        alt={t("templates.coverAlt", { title: template.title })}
        loading="lazy"
        decoding="async"
        className="absolute inset-0 h-full w-full object-cover transition-transform duration-300 ease-out group-hover:scale-[1.035] group-focus-visible:scale-[1.035]"
      />
      <div className="absolute inset-x-0 bottom-0 h-[55%] bg-gradient-to-t from-black/80 via-black/35 to-transparent" />
      <span className="absolute left-4 top-4 rounded-full border border-white/10 bg-black/35 px-2.5 py-1 text-[11px] font-medium text-white/80 backdrop-blur">
        {template.category.name}
      </span>
      <span
        aria-hidden
        className="absolute right-4 top-4 flex h-9 w-9 translate-y-1 items-center justify-center rounded-full bg-accent text-accent-ink opacity-0 transition-[opacity,transform] duration-200 group-hover:translate-y-0 group-hover:opacity-100 group-focus-visible:translate-y-0 group-focus-visible:opacity-100"
      >
        <ArrowUpRight className="h-4 w-4" />
      </span>
      <div className="absolute inset-x-4 bottom-4 flex flex-col gap-1">
        <h2 className="line-clamp-2 text-[14px] font-medium leading-5 text-white">{template.title}</h2>
        <p className="text-[12px] text-white/70">
          {template.requiredImageCount === 1
            ? t("templates.imagesOne", { count: template.requiredImageCount })
            : t("templates.images", { count: template.requiredImageCount })}
        </p>
      </div>
    </Link>
  );
}
