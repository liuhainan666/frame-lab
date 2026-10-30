"use client";

import { Image as ImageIcon, LogIn } from "lucide-react";
import Link from "next/link";

import { useAuthStore } from "@/features/auth/store";
import { useI18n } from "@/i18n/provider";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/empty-state";

export default function HomePage() {
  const { t } = useI18n();
  const status = useAuthStore((state) => state.status);

  return (
    <div>
      <header className="max-w-2xl">
        <p className="eyebrow text-accent">{t("home.eyebrow")}</p>
        <h1 className="display mt-4">{t("home.title")}</h1>
        <p className="mt-4 max-w-xl text-[14px] leading-[1.8] text-ink-secondary">
          {t("home.description")}
        </p>
      </header>

      <div className="mt-12">
        <EmptyState
          icon={<ImageIcon aria-hidden className="h-5 w-5" />}
          title={t("home.placeholderTitle")}
          description={t("home.placeholderDescription")}
          action={
            status === "anonymous" ? (
              <Link href="/login?returnTo=%2F">
                <Button leadingIcon={<LogIn aria-hidden className="h-4 w-4" />}>
                  {t("home.signIn")}
                </Button>
              </Link>
            ) : null
          }
        />
      </div>
    </div>
  );
}
