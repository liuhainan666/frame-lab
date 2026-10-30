"use client";

import { AlertTriangle, CheckCircle2, CircleSlash, Loader2 } from "lucide-react";

import { formatDateTime } from "@/i18n/format";
import { useI18n } from "@/i18n/provider";
import { cn } from "@/shared/lib/cn";

import type { AttemptStatus, TaskAttempt } from "../types";

const icons = {
  RUNNING: Loader2,
  SUCCEEDED: CheckCircle2,
  FAILED: AlertTriangle,
  INTERRUPTED: CircleSlash
} as const;

const tones: Record<AttemptStatus, string> = {
  RUNNING: "text-accent",
  SUCCEEDED: "text-success",
  FAILED: "text-danger",
  INTERRUPTED: "text-warning"
};

export function AttemptList({ attempts }: { attempts: TaskAttempt[] }) {
  const { t, locale } = useI18n();

  if (attempts.length === 0) {
    return <p className="text-[12px] text-ink-muted">{t("tasks.noAttempts")}</p>;
  }

  return (
    <ol data-testid="attempt-list" className="flex flex-col gap-2">
      {attempts.map((attempt) => {
        const Icon = icons[attempt.status];
        return (
          <li
            key={attempt.attemptNo}
            className="flex gap-2.5 rounded-[var(--radius-sm)] border border-line bg-bg/50 px-3 py-2"
          >
            <Icon
              aria-hidden
              className={cn(
                "mt-0.5 h-3.5 w-3.5 shrink-0",
                tones[attempt.status],
                attempt.status === "RUNNING" && "animate-spin"
              )}
            />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                <span className="text-[12px] font-medium text-ink">
                  {t("tasks.attempt", { n: attempt.attemptNo })}
                </span>
                <span className={cn("text-[11px] font-medium", tones[attempt.status])}>
                  {t(`tasks.attemptStatus.${attempt.status}`)}
                </span>
              </div>
              {attempt.errorMessage ? (
                <p className="text-[12px] leading-[18px] text-ink-secondary">
                  {attempt.errorMessage}
                  {attempt.errorCode ? (
                    <span className="mono ml-2 text-[10px] text-ink-muted">{attempt.errorCode}</span>
                  ) : null}
                </p>
              ) : null}
              <p className="mono text-[10px] leading-4 text-ink-muted">
                {attempt.startedAt ? formatDateTime(attempt.startedAt, locale) : "—"}
                {attempt.finishedAt ? ` → ${formatDateTime(attempt.finishedAt, locale)}` : ""}
              </p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
