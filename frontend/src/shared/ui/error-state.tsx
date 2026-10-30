import { AlertCircle } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/shared/lib/cn";
import { Button } from "@/shared/ui/button";

export function ErrorState({
  title,
  description,
  retryLabel,
  onRetry,
  action,
  className
}: {
  title: string;
  description?: string;
  retryLabel?: string;
  onRetry?: () => void;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center justify-center gap-4 rounded-[var(--radius-lg)] border border-danger/20 bg-danger-soft/40 px-6 py-16 text-center",
        className
      )}
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-danger-soft text-danger">
        <AlertCircle aria-hidden className="h-5 w-5" />
      </div>
      <div className="flex max-w-sm flex-col gap-2">
        <h2 className="h3 text-ink">{title}</h2>
        {description ? <p className="small text-ink-secondary">{description}</p> : null}
      </div>
      {action ?? (retryLabel && onRetry ? (
        <Button variant="secondary" onClick={onRetry}>
          {retryLabel}
        </Button>
      ) : null)}
    </div>
  );
}
