"use client";

import { ArrowUpRight, Image as ImageIcon, Play } from "lucide-react";
import Link from "next/link";

import { useI18n } from "@/i18n/provider";
import { backendUrl } from "@/shared/api/client";

import { templateDurationSeconds, templateEffectLabel } from "../effects";
import type { TemplateSummary } from "../types";

export function TemplateCard({
  template,
  fromCategory
}: {
  template: TemplateSummary;
  fromCategory: string;
}) {
  const { t } = useI18n();
  const isVideo = template.kind === "VIDEO";
  const seconds = templateDurationSeconds(template.output.durationMs);
  const tags = template.effectTags.slice(0, 2);
  return (
    <Link
      href={`/templates/${template.id}?fromCategory=${encodeURIComponent(fromCategory)}`}
      data-testid="template-card"
      data-kind={template.kind}
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
      <div className="absolute left-4 top-4 flex flex-wrap items-center gap-1.5">
        <span className="rounded-full border border-white/10 bg-black/35 px-2.5 py-1 text-[11px] font-medium text-white/80 backdrop-blur">
          {template.category.name}
        </span>
        <span className="inline-flex items-center gap-1 rounded-full border border-white/10 bg-black/35 px-2 py-1 text-[10px] font-medium uppercase tracking-[0.08em] text-white/70 backdrop-blur">
          {isVideo ? (
            <Play aria-hidden className="h-2.5 w-2.5 fill-current" />
          ) : (
            <ImageIcon aria-hidden className="h-2.5 w-2.5" />
          )}
          {isVideo ? t("templates.badgeVideo") : t("templates.badgeImage")}
        </span>
      </div>
      <span
        aria-hidden
        className="absolute right-4 top-4 flex h-9 w-9 translate-y-1 items-center justify-center rounded-full bg-accent text-accent-ink opacity-0 transition-[opacity,transform] duration-200 group-hover:translate-y-0 group-hover:opacity-100 group-focus-visible:translate-y-0 group-focus-visible:opacity-100"
      >
        <ArrowUpRight className="h-4 w-4" />
      </span>
      <div className="absolute inset-x-4 bottom-4 flex flex-col gap-1.5">
        <h2 className="line-clamp-2 text-[14px] font-medium leading-5 text-white">{template.title}</h2>
        {tags.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {tags.map((tag) => (
              <span
                key={tag}
                className="rounded-full bg-white/12 px-2 py-0.5 text-[10px] font-medium text-white/75"
              >
                {templateEffectLabel(tag, t)}
              </span>
            ))}
          </div>
        ) : null}
        <p className="text-[12px] text-white/70">
          {template.requiredImageCount === 1
            ? t("templates.imagesOne", { count: template.requiredImageCount })
            : t("templates.images", { count: template.requiredImageCount })}
          {isVideo && seconds ? (
            <>
              <span aria-hidden className="mx-1.5 text-white/40">·</span>
              <span className="inline-flex items-center gap-1">
                <Play aria-hidden className="h-2.5 w-2.5 fill-current" />
                {t("templates.videoDuration", { seconds })}
              </span>
            </>
          ) : null}
        </p>
      </div>
    </Link>
  );
}
