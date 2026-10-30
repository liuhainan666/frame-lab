import { cn } from "@/shared/lib/cn";

export function Skeleton({
  variant = "line",
  className
}: {
  variant?: "line" | "card" | "circle";
  className?: string;
}) {
  return (
    <div
      aria-hidden
      className={cn(
        "skeleton",
        variant === "line" && "h-4 w-full rounded-full",
        variant === "card" && "aspect-[3/4] w-full rounded-[var(--radius-lg)]",
        variant === "circle" && "h-10 w-10 rounded-full",
        className
      )}
    />
  );
}
