import { ChevronLeft, ChevronRight } from "lucide-react";

import { cn } from "@/shared/lib/cn";

export function Pagination({
  page,
  totalPages,
  onPage,
  prevLabel,
  nextLabel,
  label = "Pagination",
  className
}: {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
  prevLabel: string;
  nextLabel: string;
  label?: string;
  className?: string;
}) {
  if (totalPages <= 1) {
    return null;
  }

  const pages = pageWindow(page, totalPages);

  return (
    <nav
      aria-label={label}
      data-testid="pagination"
      className={cn("flex items-center justify-center gap-1.5", className)}
    >
      <button
        type="button"
        disabled={page <= 1}
        onClick={() => onPage(page - 1)}
        aria-label={prevLabel}
        className="flex h-10 min-w-10 items-center justify-center rounded-full border border-line px-3 text-[12px] text-ink-secondary transition-colors hover:border-line-strong hover:text-ink disabled:cursor-not-allowed disabled:opacity-35"
      >
        <ChevronLeft aria-hidden className="h-4 w-4" />
        <span className="ml-1 hidden sm:inline">{prevLabel}</span>
      </button>
      {pages.map((value, index) =>
        value === null ? (
          <span key={`gap-${index}`} className="px-1 text-ink-muted" aria-hidden>
            …
          </span>
        ) : (
          <button
            key={value}
            type="button"
            aria-current={value === page ? "page" : undefined}
            onClick={() => onPage(value)}
            className={cn(
              "h-10 min-w-10 rounded-full px-2 text-[12px] font-medium tabular transition-colors",
              value === page
                ? "bg-accent text-accent-ink"
                : "text-ink-secondary hover:bg-surface-hover hover:text-ink"
            )}
          >
            {value}
          </button>
        )
      )}
      <button
        type="button"
        disabled={page >= totalPages}
        onClick={() => onPage(page + 1)}
        aria-label={nextLabel}
        className="flex h-10 min-w-10 items-center justify-center rounded-full border border-line px-3 text-[12px] text-ink-secondary transition-colors hover:border-line-strong hover:text-ink disabled:cursor-not-allowed disabled:opacity-35"
      >
        <span className="mr-1 hidden sm:inline">{nextLabel}</span>
        <ChevronRight aria-hidden className="h-4 w-4" />
      </button>
    </nav>
  );
}

function pageWindow(page: number, totalPages: number): Array<number | null> {
  const values = new Set<number>([1, totalPages, page - 1, page, page + 1]);
  const sorted = [...values].filter((value) => value >= 1 && value <= totalPages).sort((a, b) => a - b);
  const result: Array<number | null> = [];
  for (const value of sorted) {
    if (result.length > 0 && value - (result[result.length - 1] as number) > 1) {
      result.push(null);
    }
    result.push(value);
  }
  return result;
}
