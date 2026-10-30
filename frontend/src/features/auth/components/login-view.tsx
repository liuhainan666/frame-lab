"use client";

import Link from "next/link";

import { LocaleSwitcher } from "@/features/account/components/locale-switcher";
import { LoginForm } from "@/features/auth/components/login-form";
import { useI18n } from "@/i18n/provider";
import { backendUrl } from "@/shared/api/client";
import { BrandMark } from "@/shared/ui/brand-mark";

const COVERS = [
  "travel-postcard",
  "film-memory",
  "summer-coast",
  "minimal-portrait",
  "coffee-launch",
  "wedding-invite",
  "pet-portrait",
  "brand-story",
  "christmas-letter"
];

function CoverWall() {
  const { t } = useI18n();
  return (
    <aside
      aria-label={t("auth.coverLabel")}
      className="relative hidden overflow-hidden border-r border-line lg:block"
    >
      <div aria-hidden className="absolute inset-0 grid grid-cols-3 grid-rows-3 gap-3 p-5 opacity-70">
        {COVERS.map((cover, index) => (
          <div
            key={cover}
            className="min-h-0 rounded-[var(--radius-md)] bg-surface bg-cover bg-center shadow-[var(--shadow-card)]"
            style={{
              backgroundImage: `url(${backendUrl(`/assets/templates/${cover}.webp`)})`,
              transform: index % 3 === 1 ? "translateY(22px)" : undefined
            }}
          />
        ))}
      </div>
      <div className="absolute inset-0 bg-gradient-to-b from-bg/45 via-bg/70 to-bg" />
      <div className="relative flex h-full flex-col justify-between p-10 xl:p-14">
        <div className="flex items-center gap-2.5">
          <BrandMark className="h-8 w-8 text-accent" />
          <span className="text-[16px] font-semibold tracking-[-0.02em]">FrameLab</span>
        </div>
        <div className="max-w-md">
          <p className="eyebrow text-accent">{t("auth.eyebrow")}</p>
          <h2 className="display mt-4">{t("auth.coverHeadline")}</h2>
          <p className="mt-4 max-w-sm text-[14px] leading-[1.8] text-ink-secondary">
            {t("auth.coverDescription")}
          </p>
        </div>
      </div>
    </aside>
  );
}

export function LoginView({ returnTo }: { returnTo: string }) {
  const { t } = useI18n();
  return (
    <main id="main" className="grid min-h-dvh lg:grid-cols-[55%_45%]">
      <CoverWall />
      <section className="relative flex min-h-dvh items-center justify-center px-5 py-16 sm:px-8">
        <div className="absolute right-5 top-5 sm:right-8 sm:top-7">
          <LocaleSwitcher />
        </div>
        <div className="w-full max-w-[400px]">
          <p className="eyebrow text-accent">{t("auth.eyebrow")}</p>
          <h1 className="h1 mt-3">{t("auth.title")}</h1>
          <p className="mt-3 text-[14px] leading-[1.7] text-ink-secondary">
            {t("auth.description")}
          </p>
          <LoginForm returnTo={returnTo} />
          <Link
            href="/"
            className="mt-7 inline-flex text-[12px] text-ink-muted transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          >
            {t("auth.backToTemplates")}
          </Link>
        </div>
      </section>
    </main>
  );
}
