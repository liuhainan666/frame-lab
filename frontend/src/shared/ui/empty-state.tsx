import type { ReactNode } from "react";

import { cn } from "@/shared/lib/cn";

export function EmptyState({
  icon,
  title,
  description,
  action,
  className
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-4 rounded-[var(--radius-lg)] border border-dashed border-line-strong bg-surface/45 px-6 py-16 text-center",
        className
      )}
    >
      {icon ? (
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-2 text-ink-muted">
          {icon}
        </div>
      ) : null}
      <div className="flex max-w-sm flex-col gap-2">
        <h2 className="h3 text-ink">{title}</h2>
        {description ? <p className="small text-ink-secondary">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}
