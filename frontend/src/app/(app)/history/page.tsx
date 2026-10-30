"use client";

import { Images } from "lucide-react";
import Link from "next/link";

import { RequireAuth } from "@/features/auth/components/require-auth";
import { useI18n } from "@/i18n/provider";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/empty-state";

export default function HistoryPage() {
  const { t } = useI18n();
  return (
    <RequireAuth>
      <header className="max-w-2xl">
        <p className="eyebrow text-accent">{t("history.eyebrow")}</p>
        <h1 className="display mt-4">{t("history.title")}</h1>
        <p className="mt-4 max-w-xl text-[14px] leading-[1.8] text-ink-secondary">
          {t("history.description")}
        </p>
      </header>
      <div className="mt-12">
        <EmptyState
          icon={<Images aria-hidden className="h-5 w-5" />}
          title={t("history.placeholderTitle")}
          description={t("history.placeholderDescription")}
          action={
            <Link href="/">
              <Button variant="secondary">{t("nav.templates")}</Button>
            </Link>
          }
        />
      </div>
    </RequireAuth>
  );
}
