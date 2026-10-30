"use client";

import { CheckCircle2, Clock3, RotateCw } from "lucide-react";

import { useI18n } from "@/i18n/provider";
import { formatClock, formatElapsed, formatTaskTime } from "@/i18n/format";
import { cn } from "@/shared/lib/cn";
import { useObjectUrl } from "@/shared/lib/use-object-url";
import { backendUrl } from "@/shared/api/client";
import { Skeleton } from "@/shared/ui/skeleton";

import { displayStatus, stageLabelKey } from "../status";
import type { TaskSummary } from "../types";
import { StageStepper } from "./stage-stepper";
import { StatusBadge } from "./status-badge";

function retrySeconds(nextRetryAt: string | null): number | null {
  if (!nextRetryAt) {
    return null;
  }
  return Math.max(0, Math.round((new Date(nextRetryAt).getTime() - Date.now()) / 1000));
}

export function TaskCard({
  task,
  onOpen,
  stale = false,
  highlight = false
}: {
  task: TaskSummary;
  onOpen: (task: TaskSummary) => void;
  stale?: boolean;
  highlight?: boolean;
}) {
  const { t, locale } = useI18n();
  const effective = displayStatus(task);
  const resultUrl = task.status === "SUCCEEDED" ? task.result?.url ?? null : null;
  const result = useObjectUrl(resultUrl, Boolean(resultUrl));
  const stageKey = stageLabelKey(task.currentStage);
  const failedStageKey = stageLabelKey(task.failure?.stage ?? task.currentStage ?? null);
  const time = formatTaskTime(
    task.status === "SUCCEEDED" ? task.finishedAt ?? task.updatedAt : task.createdAt,
    locale,
    t("time.today"),
    t("time.yesterday")
  );
  const seconds = retrySeconds(task.retry.nextRetryAt);
  const eta =
    seconds === null
      ? null
      : seconds > 30 && task.retry.nextRetryAt
        ? t("tasks.nextRetryAt", { time: formatClock(task.retry.nextRetryAt, locale) })
        : t("tasks.retryEta", { seconds });
  const topLine =
    effective === "RETRYING" || effective === "RESUMING"
      ? "bg-warning"
      : effective === "RUNNING"
        ? "bg-accent"
        : effective === "FAILED"
          ? "bg-danger"
          : null;

  return (
    <button
      type="button"
      data-testid="task-card"
      data-status={effective}
      onClick={() => onOpen(task)}
      className={cn(
        "group relative block aspect-[3/4] w-full overflow-hidden rounded-[var(--radius-lg)] border border-line bg-surface text-left shadow-[var(--shadow-card)]",
        "transition-[transform,border-color,box-shadow] duration-200 hover:-translate-y-0.5 hover:border-accent-border",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg",
        highlight && "border-accent shadow-[0_0_0_1px_var(--accent-border),var(--shadow-card)]"
      )}
    >
      {topLine ? <span aria-hidden className={cn("absolute inset-x-0 top-0 z-10 h-0.5", topLine)} /> : null}

      {task.status === "SUCCEEDED" && result.status === "ready" && result.url ? (
        <img
          src={result.url}
          alt={t("tasks.resultAlt", { title: task.templateTitle })}
          className="absolute inset-0 h-full w-full object-cover"
        />
      ) : (
        <>
          <img
            src={backendUrl(task.templateCoverUrl)}
            alt={t("templates.coverAlt", { title: task.templateTitle })}
            loading="lazy"
            decoding="async"
            className={cn(
              "absolute inset-0 h-full w-full object-cover",
              task.status === "SUCCEEDED" ? "opacity-30" : "opacity-[0.16]"
            )}
          />
          <div className="absolute inset-0 bg-gradient-to-b from-bg/20 via-bg/70 to-bg" />
        </>
      )}

      {task.status === "SUCCEEDED" && result.status === "loading" ? (
        <Skeleton className="absolute inset-0 rounded-none" />
      ) : null}

      <div className="relative flex h-full flex-col justify-between p-4">
        <div className="flex items-start justify-between gap-2">
          <div role="status" aria-live="polite">
            <StatusBadge status={effective} />
          </div>
          {stale ? (
            <span
              aria-label={t("history.stale")}
              title={t("history.stale")}
              className="mt-1 h-2 w-2 rounded-full bg-warning"
            />
          ) : null}
        </div>

        <div className="flex min-h-0 flex-1 flex-col justify-center py-4">
          {effective === "QUEUED" ? (
            <div className="flex flex-col gap-2">
              <Clock3 aria-hidden className="h-5 w-5 text-ink-muted" />
              <p className="text-[12px] leading-[18px] text-ink-secondary">{t("tasks.queuedHint")}</p>
              <p className="mono text-[11px] text-ink-muted">{time}</p>
            </div>
          ) : null}

          {effective === "RESUMING" ? (
            <div className="flex flex-col gap-2">
              <RotateCw aria-hidden className="h-5 w-5 text-warning motion-safe:animate-[breathe_2s_ease-in-out_infinite]" />
              <p className="text-[12px] leading-[18px] text-ink-secondary">{t("tasks.resumingHint")}</p>
              {stageKey ? <p className="text-[11px] text-ink-muted">{t(stageKey)}</p> : null}
            </div>
          ) : null}

          {effective === "RUNNING" ? (
            <div className="flex flex-col gap-3">
              <StageStepper currentStage={task.currentStage} status={task.status} />
              <p className="text-[12px] leading-[18px] text-ink-secondary">
                {t("tasks.attempt", { n: Math.max(1, task.retry.attemptCount) })}
                <span className="mx-1 text-ink-muted">·</span>
                <span className="mono text-[11px] text-ink-muted">{formatElapsed(task.createdAt)}</span>
              </p>
            </div>
          ) : null}

          {effective === "RETRYING" ? (
            <div className="flex flex-col gap-2">
              {failedStageKey ? (
                <p className="text-[11px] font-medium text-warning">{t(failedStageKey)}</p>
              ) : null}
              <p className="line-clamp-2 text-[12px] leading-[18px] text-ink-secondary">
                {task.retry.lastError?.message}
              </p>
              <p className="text-[11px] text-ink-muted">
                {t("tasks.retryAttempts", {
                  current: task.retry.attemptCount,
                  max: task.retry.maxAttempts
                })}
                {eta ? <span className="ml-2 text-warning">{eta}</span> : null}
              </p>
            </div>
          ) : null}

          {effective === "FAILED" ? (
            <div className="flex flex-col gap-2">
              {failedStageKey ? (
                <p className="text-[11px] font-medium text-danger">{t(failedStageKey)}</p>
              ) : null}
              <p className="line-clamp-2 text-[12px] leading-[18px] text-ink-secondary">
                {task.failure?.message}
              </p>
              <p className="text-[11px] text-ink-muted">
                {t("tasks.retryAttempts", {
                  current: task.retry.attemptCount,
                  max: task.retry.maxAttempts
                })}
              </p>
            </div>
          ) : null}

          {effective === "SUCCEEDED" ? (
            <div className="flex flex-1 items-end justify-end">
              <CheckCircle2 aria-hidden className="h-5 w-5 text-success" />
            </div>
          ) : null}
        </div>

        <div className="flex flex-col gap-1">
          <p className="line-clamp-2 text-[13px] font-medium leading-[18px] text-ink">
            {task.templateTitle}
          </p>
          <p className="mono text-[11px] text-ink-muted">
            {task.status === "SUCCEEDED"
              ? t("tasks.completedAt", { time })
              : t("tasks.createdAt", { time })}
          </p>
          {task.status === "SUCCEEDED" ? (
            <span className="mt-1 inline-flex items-center gap-1 text-[11px] text-accent opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100">
              {t("tasks.viewResult")}
            </span>
          ) : null}
        </div>
      </div>
    </button>
  );
}
