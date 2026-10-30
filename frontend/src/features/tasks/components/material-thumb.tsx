"use client";

import { useObjectUrl } from "@/shared/lib/use-object-url";
import { Skeleton } from "@/shared/ui/skeleton";

export function MaterialThumb({
  path,
  index,
  alt
}: {
  path: string;
  index: number;
  alt: string;
}) {
  const image = useObjectUrl(path);
  return (
    <div className="relative aspect-[4/3] overflow-hidden rounded-[var(--radius-sm)] border border-line bg-bg">
      {image.status !== "ready" || !image.url ? <Skeleton className="absolute inset-0 rounded-none" /> : null}
      {image.url ? (
        <img
          src={image.url}
          alt={alt}
          loading="lazy"
          decoding="async"
          className="relative h-full w-full object-cover"
        />
      ) : null}
      <span className="mono absolute left-1.5 top-1.5 rounded bg-black/55 px-1.5 py-0.5 text-[10px] text-white/80">
        {String(index).padStart(2, "0")}
      </span>
    </div>
  );
}
