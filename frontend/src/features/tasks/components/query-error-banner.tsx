"use client";

import { AlertTriangle, RotateCw } from "lucide-react";

import { useI18n } from "@/i18n/provider";

export function QueryErrorBanner({
  message,
  onRetry,
  retrying = false
}: {
  message: string;
  onRetry: () => void;
  retrying?: boolean;
}) {
  const { t } = useI18n();
  return (
    <div
      role="status"
      data-testid="query-error-banner"
      className="flex items-center justify-between gap-4 rounded-[var(--radius-md)] border border-warning/25 bg-warning-soft px-4 py-3 text-[12px] text-warning"
    >
      <span className="flex items-center gap-2">
        <AlertTriangle aria-hidden className="h-4 w-4 shrink-0" />
        {message}
      </span>
      <button
        type="button"
        onClick={onRetry}
        className="flex shrink-0 items-center gap-1.5 rounded-full border border-warning/30 px-3 py-1.5 font-medium transition-colors hover:bg-warning/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-warning"
      >
        <RotateCw aria-hidden className={retrying ? "h-3.5 w-3.5 animate-spin" : "h-3.5 w-3.5"} />
        {t("common.retry")}
      </button>
    </div>
  );
}
