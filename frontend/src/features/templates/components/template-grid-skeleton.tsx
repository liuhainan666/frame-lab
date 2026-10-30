import { Skeleton } from "@/shared/ui/skeleton";

export function TemplateGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div
      aria-busy
      className="grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6"
    >
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className="relative aspect-[3/4] overflow-hidden rounded-[var(--radius-lg)] border border-line bg-surface"
        >
          <Skeleton className="absolute inset-0 rounded-none" />
          <div className="absolute inset-x-4 bottom-4 flex flex-col gap-2">
            <Skeleton className="h-3.5 w-3/4 rounded-full bg-white/10" />
            <Skeleton className="h-2.5 w-1/2 rounded-full bg-white/10" />
          </div>
        </div>
      ))}
    </div>
  );
}
