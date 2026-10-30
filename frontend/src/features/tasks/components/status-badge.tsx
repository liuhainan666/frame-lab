"use client";

import { AlertTriangle, CheckCircle2, Clock3, Loader2, RotateCw } from "lucide-react";

import { useI18n } from "@/i18n/provider";
import { cn } from "@/shared/lib/cn";
import { statusLabelKey, statusTone, type DisplayStatus } from "../status";

const icons = {
  QUEUED: Clock3,
  RESUMING: RotateCw,
  RUNNING: Loader2,
  RETRYING: RotateCw,
  SUCCEEDED: CheckCircle2,
  FAILED: AlertTriangle
} as const;

const toneClasses = {
  neutral: "border-line bg-surface-2 text-ink-secondary",
  accent: "border-accent-border bg-accent-soft text-accent",
  success: "border-success/25 bg-success-soft text-success",
  warning: "border-warning/25 bg-warning-soft text-warning",
  danger: "border-danger/25 bg-danger-soft text-danger"
} as const;

export function StatusBadge({
  status,
  className
}: {
  status: DisplayStatus;
  className?: string;
}) {
  const { t } = useI18n();
  const Icon = icons[status];
  const tone = statusTone(status);
  return (
    <span
      data-testid="task-status"
      className={cn(
        "inline-flex h-6 items-center gap-1.5 rounded-full border px-2.5 text-[11px] font-medium",
        toneClasses[tone],
        className
      )}
    >
      <Icon
        aria-hidden
        className={cn(
          "h-3 w-3",
          status === "RUNNING" && "animate-spin",
          status === "RETRYING" && "motion-safe:animate-[breathe_2s_ease-in-out_infinite]"
        )}
      />
      {t(statusLabelKey(status))}
    </span>
  );
}
