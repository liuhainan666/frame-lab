"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { useI18n } from "@/i18n/provider";
import { Skeleton } from "@/shared/ui/skeleton";

import { useAuthStore } from "../store";

export function RequireAuth({ children }: { children: ReactNode }) {
  const status = useAuthStore((state) => state.status);
  const pathname = usePathname();
  const router = useRouter();
  const { t } = useI18n();

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(`/login?returnTo=${encodeURIComponent(pathname)}`);
    }
  }, [pathname, router, status]);

  if (status === "authenticated") {
    return <>{children}</>;
  }

  if (status === "anonymous") {
    return null;
  }

  return (
    <div className="flex flex-col gap-4 pt-10" aria-busy aria-label={t("common.loading")}>
      <Skeleton className="h-10 w-56" />
      <Skeleton className="h-4 w-80 max-w-full" />
      <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        <Skeleton variant="card" />
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    </div>
  );
}
