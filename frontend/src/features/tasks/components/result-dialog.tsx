"use client";

import { ImageOff, RotateCw } from "lucide-react";
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
  const image = useObjectUrl(task?.result?.url ?? null, open);

  if (!task) {
    return null;
  }

  const width = task.result?.width ?? 0;
  const height = task.result?.height ?? 0;
  const size = image.size;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        title={t("tasks.resultTitle")}
        description={task.templateTitle}
        size="wide"
        footer={
          <div className="flex w-full flex-wrap items-center justify-between gap-3 border-t border-line pt-4 text-[12px] text-ink-muted">
            <span className="mono tabular">
              {width && height
                ? t("tasks.resultMeta", {
                    width,
                    height,
                    size: size === null ? "—" : formatFileSize(size, locale)
                  })
                : null}
            </span>
            <span className="mono">{t("tasks.shortCode")}: {task.id.slice(0, 8)}</span>
          </div>
        }
      >
        <div className="relative flex min-h-[320px] items-center justify-center overflow-hidden rounded-[var(--radius-md)] border border-line bg-bg">
          {image.status === "loading" ? <Skeleton className="absolute inset-0 rounded-none" /> : null}
          {image.status === "ready" && image.url ? (
            <img
              key={retryKey}
              src={image.url}
              alt={t("tasks.resultAlt", { title: task.templateTitle })}
              className="max-h-[70vh] w-auto max-w-full object-contain"
            />
          ) : null}
          {image.status === "error" ? (
            <div className="flex flex-col items-center gap-3 p-8 text-center text-ink-secondary">
              <ImageOff aria-hidden className="h-6 w-6 text-danger" />
              <p className="small">{t("tasks.imageError")}</p>
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<RotateCw aria-hidden className="h-3.5 w-3.5" />}
                onClick={() => {
                  setRetryKey((value) => value + 1);
                  image.retry();
                }}
              >
                {t("tasks.imageRetry")}
              </Button>
            </div>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}
