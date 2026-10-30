import type { ButtonHTMLAttributes, ReactNode } from "react";

import { cn } from "@/shared/lib/cn";

export type ChipProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  selected?: boolean;
  children: ReactNode;
};

export function Chip({ selected = false, className, children, type = "button", ...props }: ChipProps) {
  return (
    <button
      type={type}
      aria-pressed={selected}
      className={cn(
        "inline-flex h-[34px] shrink-0 items-center rounded-full border px-3.5 text-[13px] font-medium transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg",
        selected
          ? "border-accent-border bg-accent-soft text-accent"
          : "border-line bg-surface text-ink-secondary hover:border-line-strong hover:text-ink",
        className
      )}
      {...props}
    >
      {children}
    </button>
  );
}
