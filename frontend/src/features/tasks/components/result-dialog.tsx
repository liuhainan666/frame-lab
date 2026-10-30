"use client";

import { ImageOff, RotateCw, VideoOff } from "lucide-react";
import { useState } from "react";

import { useI18n } from "@/i18n/provider";
import { formatFileSize } from "@/i18n/format";
import { useObjectUrl } from "@/shared/lib/use-object-url";
import { Button } from "@/shared/ui/button";
import { Dialog, DialogContent } from "@/shared/ui/dialog";
import { Skeleton } from "@/shared/ui/skeleton";

import type { TaskSummary } from "../types";

export function ResultDialog({
  task,
  open,
  onOpenChange
}: {
  task: TaskSummary | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t, locale } = useI18n();
  const [retryKey, setRetryKey] = useState(0);
  const isVideo = task?.result?.kind === "VIDEO";
  const media = useObjectUrl(task?.result?.url ?? null, open);
  const poster = useObjectUrl(isVideo ? task?.result?.posterUrl ?? null : null, open && isVideo);

  if (!task) {
    return null;
  }

  const width = task.result?.width ?? 0;
  const height = task.result?.height ?? 0;
  const durationMs = task.result?.durationMs ?? null;
  const size = media.size;

  const meta =
    width && height
      ? isVideo && durationMs
        ? t("tasks.videoMeta", {
            width,
            height,
            duration: t("tasks.durationSeconds", { seconds: Math.round(durationMs / 1000) }),
            size: size === null ? "—" : formatFileSize(size, locale)
          })
        : t("tasks.resultMeta", {
            width,
            height,
            size: size === null ? "—" : formatFileSize(size, locale)
          })
      : null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        title={t("tasks.resultTitle")}
        description={task.templateTitle}
        size="wide"
        footer={
          <div className="flex w-full flex-wrap items-center justify-between gap-3 border-t border-line pt-4 text-[12px] text-ink-muted">
            <span className="mono tabular">{meta}</span>
            <span className="mono">{t("tasks.shortCode")}: {task.id.slice(0, 8)}</span>
          </div>
        }
      >
        <div className="relative flex min-h-[320px] items-center justify-center overflow-hidden rounded-[var(--radius-md)] border border-line bg-bg">
          {media.status === "loading" ? <Skeleton className="absolute inset-0 rounded-none" /> : null}
          {media.status === "ready" && media.url ? (
            isVideo ? (
              <video
                key={retryKey}
                src={media.url}
                poster={poster.url ?? undefined}
                controls
                playsInline
                preload="metadata"
                aria-label={t("tasks.resultAlt", { title: task.templateTitle })}
                className="max-h-[70vh] max-w-full"
              />
            ) : (
              <img
                key={retryKey}
                src={media.url}
                alt={t("tasks.resultAlt", { title: task.templateTitle })}
                className="max-h-[70vh] w-auto max-w-full object-contain"
              />
            )
          ) : null}
          {media.status === "error" ? (
            <div className="flex flex-col items-center gap-3 p-8 text-center text-ink-secondary">
              {isVideo ? (
                <VideoOff aria-hidden className="h-6 w-6 text-danger" />
              ) : (
                <ImageOff aria-hidden className="h-6 w-6 text-danger" />
              )}
              <p className="small">{isVideo ? t("tasks.videoError") : t("tasks.imageError")}</p>
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<RotateCw aria-hidden className="h-3.5 w-3.5" />}
                onClick={() => {
                  setRetryKey((value) => value + 1);
                  media.retry();
                  if (isVideo) {
                    poster.retry();
                  }
                }}
              >
                {isVideo ? t("tasks.videoRetry") : t("tasks.imageRetry")}
              </Button>
            </div>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}
