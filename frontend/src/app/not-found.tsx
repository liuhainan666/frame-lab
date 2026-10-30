"use client";

import Link from "next/link";

import { useI18n } from "@/i18n/provider";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/empty-state";

export default function NotFound() {
  const { t } = useI18n();
  return (
    <main id="main" className="page-shell flex min-h-dvh items-center justify-center py-16">
      <EmptyState
        title={t("errors.notFound")}
        action={
          <Link href="/">
            <Button variant="secondary">{t("nav.templates")}</Button>
          </Link>
        }
        className="w-full max-w-xl"
      />
    </main>
  );
}
