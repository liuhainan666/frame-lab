"use client";

import { Images, LayoutGrid, LogIn } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { AccountMenu } from "@/features/account/components/account-menu";
import { LocaleSwitcher } from "@/features/account/components/locale-switcher";
import { useAuthStore } from "@/features/auth/store";
import { useI18n } from "@/i18n/provider";
import { cn } from "@/shared/lib/cn";
import { BrandMark } from "@/shared/ui/brand-mark";
import { Skeleton } from "@/shared/ui/skeleton";

export function AppHeader() {
  const { t } = useI18n();
  const pathname = usePathname();
  const status = useAuthStore((state) => state.status);
  const user = useAuthStore((state) => state.user);

  const navItems = [
    { href: "/", label: t("nav.templates"), icon: LayoutGrid, active: pathname === "/" },
    {
      href: "/history",
      label: t("nav.myWork"),
      icon: Images,
      active: pathname.startsWith("/history")
    }
  ];

  return (
    <header className="sticky top-0 z-40 h-[68px] border-b border-line bg-bg/75 backdrop-blur-xl">
      <div className="page-shell flex h-full items-center gap-3 sm:gap-8">
        <Link
          href="/"
          aria-label={t("app.name")}
          className="flex shrink-0 items-center gap-2.5 text-ink transition-opacity hover:opacity-85 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        >
          <BrandMark className="h-7 w-7 text-accent" />
          <span className="hidden text-[15px] font-semibold tracking-[-0.02em] sm:inline">
            FrameLab
          </span>
        </Link>

        <nav aria-label={t("nav.label")} className="flex items-center gap-1 sm:gap-2">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-label={item.label}
                aria-current={item.active ? "page" : undefined}
                className={cn(
                  "relative flex h-10 items-center gap-2 rounded-full px-2.5 text-[13px] font-medium transition-colors sm:px-3",
                  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent",
                  item.active ? "text-ink" : "text-ink-secondary hover:bg-surface-hover hover:text-ink"
                )}
              >
                <Icon aria-hidden className="h-4 w-4 sm:hidden" />
                <span className="hidden sm:inline">{item.label}</span>
                {item.active ? (
                  <span className="absolute inset-x-3 -bottom-[14px] h-0.5 rounded-full bg-accent" />
                ) : null}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-2 sm:gap-3">
          <span className="hidden sm:block">
            <LocaleSwitcher />
          </span>
          <span className="sm:hidden">
            <LocaleSwitcher compact />
          </span>
          {status === "authenticated" && user ? (
            <AccountMenu />
          ) : status === "unknown" ? (
            <Skeleton variant="circle" className="h-9 w-9" />
          ) : (
            <Link
              href={`/login?returnTo=${encodeURIComponent(pathname)}`}
              className="flex h-9 items-center gap-2 rounded-full border border-line-strong px-3 text-[12px] font-medium text-ink-secondary transition-colors hover:border-accent-border hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent sm:px-4"
            >
              <LogIn aria-hidden className="h-3.5 w-3.5" />
              {t("nav.login")}
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
