"use client";

import {
  AlertTriangle,
  CheckCircle2,
  Circle,
  ImageOff,
  Loader2,
  MinusCircle,
  RotateCw
} from "lucide-react";
import { useState } from "react";

import { formatDateTime } from "@/i18n/format";
import { useI18n } from "@/i18n/provider";
import { backendUrl } from "@/shared/api/client";
import { cn } from "@/shared/lib/cn";
import { useObjectUrl } from "@/shared/lib/use-object-url";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Dialog, DialogContent } from "@/shared/ui/dialog";
import { Skeleton } from "@/shared/ui/skeleton";

import { useTask } from "../queries";
import { displayStatus, stageLabelKey } from "../status";
import type { StepStatus, TaskDetail, TaskStep } from "../types";
import { AttemptList } from "./attempt-list";
import { MaterialThumb } from "./material-thumb";
import { ResultDialog } from "./result-dialog";
import { StageStepper } from "./stage-stepper";
import { StatusBadge } from "./status-badge";

const stepIcons = {
  PENDING: Circle,
  RUNNING: Loader2,
  SUCCEEDED: CheckCircle2,
  FAILED: AlertTriangle,
  SKIPPED: MinusCircle
} as const;

const stepTones: Record<StepStatus, string> = {
  PENDING: "text-ink-muted",
  RUNNING: "text-accent",
  SUCCEEDED: "text-success",
  FAILED: "text-danger",
  SKIPPED: "text-ink-muted"
};

function StepRow({ step }: { step: TaskStep }) {
  const { t, locale } = useI18n();
  const labelKey = stageLabelKey(step.stage);
  const Icon = stepIcons[step.status];

  return (
    <li>
      <details className="group rounded-[var(--radius-sm)] border border-line bg-surface-2">
        <summary className="flex cursor-pointer list-none items-center gap-2.5 px-3 py-2.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent [&::-webkit-details-marker]:hidden">
          <Icon
            aria-hidden
            className={cn(
              "h-3.5 w-3.5 shrink-0",
              stepTones[step.status],
              step.status === "RUNNING" && "animate-spin"
            )}
          />
          <span className="text-[12px] font-medium text-ink">{labelKey ? t(labelKey) : step.stage}</span>
          <span className={cn("text-[11px]", stepTones[step.status])}>
            {t(`tasks.stepStatus.${step.status}`)}
          </span>
          <span className="mono ml-auto text-[10px] text-ink-muted">
            {t("tasks.attemptCount", { count: step.attemptCount })}
          </span>
        </summary>
        <div className="flex flex-col gap-2 border-t border-line px-3 py-2.5">
          {step.message ? (
            <p className="text-[12px] leading-[18px] text-ink-secondary">{step.message}</p>
          ) : null}
          {step.errorCode ? (
            <p className="mono text-[10px] text-danger">{step.errorCode}</p>
          ) : null}
          <AttemptList attempts={step.attempts} />
          {step.finishedAt ? (
            <p className="mono text-[10px] text-ink-muted">
              {formatDateTime(step.finishedAt, locale)}
            </p>
          ) : null}
        </div>
      </details>
    </li>
  );
}

function DetailBody({ task }: { task: TaskDetail }) {
  const { t, locale } = useI18n();
  const [resultOpen, setResultOpen] = useState(false);
  const effective = displayStatus(task);
  const succeeded = task.status === "SUCCEEDED";
  const isVideo = task.result?.kind === "VIDEO";
  const result = useObjectUrl(succeeded ? task.result?.url : null, succeeded);
  const coverUrl = task.templateCoverUrl ? backendUrl(task.templateCoverUrl) : null;
  const failureStageKey = stageLabelKey(task.failure?.stage ?? null);

  return (
    <>
      <div className="grid gap-6 md:grid-cols-[300px_minmax(0,1fr)]">
        <div className="flex flex-col gap-4">
          <div className="relative aspect-[4/3] overflow-hidden rounded-[var(--radius-md)] border border-line bg-bg">
            {succeeded ? (
              <>
                {result.status === "loading" ? <Skeleton className="absolute inset-0 rounded-none" /> : null}
                {result.url ? (
                  isVideo ? (
                    <video
                      src={result.url}
                      controls
                      playsInline
                      preload="metadata"
                      aria-label={t("tasks.resultAlt", { title: task.templateTitle })}
                      className="h-full w-full object-contain"
                    />
                  ) : (
                    <img
                      src={result.url}
                      alt={t("tasks.resultAlt", { title: task.templateTitle })}
                      className="h-full w-full object-contain"
                    />
                  )
                ) : null}
                {result.status === "error" ? (
                  <div className="flex h-full flex-col items-center justify-center gap-2 text-ink-muted">
                    <ImageOff aria-hidden className="h-5 w-5" />
                    <span className="text-[12px]">
                      {isVideo ? t("tasks.videoError") : t("tasks.imageError")}
                    </span>
                  </div>
                ) : null}
              </>
            ) : coverUrl ? (
              <img
                src={coverUrl}
                alt={t("templates.coverAlt", { title: task.templateTitle })}
                className="h-full w-full object-cover opacity-80"
              />
            ) : null}
          </div>

          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between gap-3">
              <h3 className="h3">{t("tasks.materials")}</h3>
              <span className="mono text-[11px] text-ink-muted">
                {task.canvas.width} × {task.canvas.height}
              </span>
            </div>
            {task.materials.length > 0 ? (
              <div className="grid grid-cols-2 gap-2">
                {task.materials.map((material) => (
                  <MaterialThumb
                    key={material.assetId}
                    path={material.contentUrl}
                    index={material.slotIndex}
                    alt={t("tasks.materialAlt", {
                      title: task.templateTitle,
                      index: material.slotIndex
                    })}
                  />
                ))}
              </div>
            ) : (
              <p className="text-[12px] text-ink-muted">—</p>
            )}
          </div>

          {succeeded && task.result?.available ? (
            <Button
              size="md"
              leadingIcon={<CheckCircle2 aria-hidden className="h-4 w-4" />}
              onClick={() => setResultOpen(true)}
            >
              {t("tasks.viewResult")}
            </Button>
          ) : null}
        </div>

        <div className="flex min-w-0 flex-col gap-5">
          <div className="flex flex-wrap items-center gap-3">
            <StatusBadge status={effective} />
            {task.failure?.simulated || task.retry.lastError?.simulated ? (
              <Badge tone="warning">{t("tasks.simulated")}</Badge>
            ) : null}
            <span className="mono ml-auto text-[11px] text-ink-muted">
              {formatDateTime(task.createdAt, locale)}
            </span>
          </div>

          <StageStepper currentStage={task.currentStage} status={task.status} steps={task.steps} />

          {effective === "RETRYING" ? (
            <div className="rounded-[var(--radius-md)] border border-warning/25 bg-warning-soft px-4 py-3">
              <p className="text-[12px] font-medium text-warning">{t("tasks.retryWait")}</p>
              <p className="mt-1 text-[12px] leading-[18px] text-ink-secondary">
                {t("tasks.retryAttempts", {
                  current: task.retry.attemptCount,
                  max: task.retry.maxAttempts
                })}
                {task.retry.nextRetryAt ? (
                  <span className="ml-2">
                    {t("tasks.nextRetryAt", { time: formatDateTime(task.retry.nextRetryAt, locale) })}
                  </span>
                ) : null}
              </p>
              {task.retry.lastError?.message ? (
                <p className="mt-1.5 text-[12px] leading-[18px] text-ink-secondary">
                  {task.retry.lastError.message}
                  <span className="mono ml-2 text-[10px] text-ink-muted">
                    {task.retry.lastError.code}
                  </span>
                </p>
              ) : null}
            </div>
          ) : null}

          {task.status === "FAILED" && task.failure ? (
            <div className="rounded-[var(--radius-md)] border border-danger/25 bg-danger-soft px-4 py-3">
              <p className="text-[12px] font-medium text-danger">{t("tasks.failureTitle")}</p>
              <p className="mt-1 text-[12px] leading-[18px] text-ink-secondary">
                {task.failure.message}
              </p>
              <p className="mono mt-1.5 text-[10px] text-ink-muted">
                {failureStageKey ? `${t(failureStageKey)} · ` : ""}
                {task.failure.code}
              </p>
            </div>
          ) : null}

          <div className="flex flex-col gap-2">
            <h3 className="h3">{t("tasks.steps")}</h3>
            <ol className="flex flex-col gap-2">
              {task.steps.map((step) => (
                <StepRow key={step.seq} step={step} />
              ))}
            </ol>
          </div>
        </div>
      </div>

      <ResultDialog task={task} open={resultOpen} onOpenChange={setResultOpen} />
    </>
  );
}

export function TaskDetailDialog({
  taskId,
  open,
  onOpenChange
}: {
  taskId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  const query = useTask(taskId, open);
  const task = query.data ?? null;
  const loading = query.isPending && !task;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        title={t("tasks.detailTitle")}
        description={task?.templateTitle ?? t("common.loading")}
        size="wide"
        footer={
          task ? (
            <div className="flex w-full flex-wrap items-center justify-between gap-3 border-t border-line pt-4 text-[12px] text-ink-muted">
              <span className="mono">
                {t("tasks.shortCode")}: {task.id.slice(0, 8)}
              </span>
              <span className="mono">
                {t("tasks.attempts")}: {task.retry.attemptCount}
              </span>
            </div>
          ) : null
        }
      >
        {loading ? (
          <div className="grid gap-6 md:grid-cols-[300px_minmax(0,1fr)]">
            <Skeleton className="aspect-[3/4] w-full" />
            <div className="flex flex-col gap-3">
              <Skeleton className="h-6 w-28" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-24 w-full" />
            </div>
          </div>
        ) : null}

        {query.isError && !task ? (
          <div role="alert" className="flex flex-col items-start gap-3 py-6">
            <p className="text-[13px] text-ink-secondary">{t("tasks.loadError")}</p>
            <Button
              variant="secondary"
              size="sm"
              leadingIcon={<RotateCw aria-hidden className="h-3.5 w-3.5" />}
              onClick={() => void query.refetch()}
            >
              {t("tasks.retry")}
            </Button>
          </div>
        ) : null}

        {task ? <DetailBody task={task} /> : null}
      </DialogContent>
    </Dialog>
  );
}
