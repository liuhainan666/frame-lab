import { Skeleton } from "@/shared/ui/skeleton";

export function TaskGridSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-4" aria-busy>
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className="relative aspect-[3/4] overflow-hidden rounded-[var(--radius-lg)] border border-line bg-surface p-4"
        >
          <Skeleton className="h-6 w-24 rounded-full" />
          <div className="absolute inset-x-4 bottom-4 flex flex-col gap-2">
            <Skeleton className="h-3.5 w-2/3 rounded-full" />
            <Skeleton className="h-2.5 w-1/3 rounded-full" />
          </div>
        </div>
      ))}
    </div>
  );
}
