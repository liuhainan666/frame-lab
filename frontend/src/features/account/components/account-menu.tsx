"use client";

import { ChevronRight, LogOut, Settings2, ShieldOff } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { logout, logoutAll } from "@/features/auth/api";
import { useAuthStore } from "@/features/auth/store";
import { formatDate } from "@/i18n/format";
import { useI18n } from "@/i18n/provider";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Dialog, DialogContent, DialogTrigger } from "@/shared/ui/dialog";
import { BrandMark } from "@/shared/ui/brand-mark";
import { useQueryClient } from "@tanstack/react-query";

import { LocaleSwitcher } from "./locale-switcher";

export function AccountMenu() {
  const { t, locale } = useI18n();
  const user = useAuthStore((state) => state.user);
  const [open, setOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [signingOutAll, setSigningOutAll] = useState(false);
  const queryClient = useQueryClient();

  if (!user) {
    return null;
  }

  const initial = Array.from(user.displayName.trim())[0] ?? user.email.slice(0, 1);

  async function handleSignOut() {
    setSigningOut(true);
    try {
      await logout();
    } finally {
      queryClient.clear();
      setOpen(false);
      setSigningOut(false);
      window.location.assign("/");
    }
  }

  async function handleSignOutAll() {
    setSigningOutAll(true);
    try {
      await logoutAll();
    } finally {
      queryClient.clear();
      setOpen(false);
      setSigningOutAll(false);
      window.location.assign("/");
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <button
          type="button"
          aria-label={t("nav.openAccount")}
          className="flex h-9 items-center gap-2 rounded-full border border-line bg-bg-elevated/80 pl-1 pr-3 transition-colors hover:border-line-strong hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-accent text-[12px] font-semibold text-accent-ink">
            {initial}
          </span>
          <span className="hidden max-w-[120px] truncate text-[13px] font-medium text-ink sm:inline">
            {user.displayName}
          </span>
        </button>
      </DialogTrigger>
      <DialogContent title={t("account.title")} description={user.email}>
        <div className="flex items-center gap-4">
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-gradient-to-br from-accent to-[#7e9a2c] text-xl font-semibold text-accent-ink">
            {initial}
          </span>
          <div className="flex min-w-0 flex-col gap-1">
            <p className="text-[18px] font-semibold text-ink">{user.displayName}</p>
            <p className="small truncate text-ink-muted">{user.email}</p>
            <Badge tone={user.role === "ADMIN" ? "warning" : "neutral"}>
              {user.role === "ADMIN" ? t("account.roleAdmin") : t("account.roleUser")}
            </Badge>
          </div>
        </div>
        <dl className="mt-6 flex flex-col gap-3 border-t border-line pt-5 text-[13px]">
          <div className="flex items-center justify-between gap-4">
            <dt className="text-ink-muted">{t("account.joinedLabel")}</dt>
            <dd className="mono text-ink-secondary">{formatDate(user.createdAt, locale)}</dd>
          </div>
          <div className="flex items-center justify-between gap-4">
            <dt className="text-ink-muted">{t("account.languageLabel")}</dt>
            <dd>
              <LocaleSwitcher />
            </dd>
          </div>
        </dl>
        <p className="mt-3 text-[12px] leading-[18px] text-ink-muted">
          {t("account.languageHint")}
        </p>
        <p className="mt-4 flex items-center gap-2 text-[12px] text-ink-muted">
          <BrandMark className="h-4 w-4 shrink-0 text-accent" />
          {t("account.scope")}
        </p>
        <Link
          href="/settings"
          onClick={() => setOpen(false)}
          className="mt-5 flex items-center justify-between gap-4 rounded-xl border border-line bg-surface/60 px-4 py-3 text-[13px] font-medium text-ink-secondary transition-colors hover:border-line-strong hover:bg-surface-hover hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        >
          <span className="flex items-center gap-2">
            <Settings2 aria-hidden className="h-4 w-4" />
            {t("settings.title")}
          </span>
          <ChevronRight aria-hidden className="h-4 w-4 text-ink-muted" />
        </Link>
        <div className="mt-5 border-t border-line pt-5">
          <Button
            variant="ghost"
            leadingIcon={<LogOut aria-hidden className="h-4 w-4" />}
            loading={signingOut}
            disabled={signingOutAll}
            onClick={handleSignOut}
            className="w-full justify-start text-danger hover:bg-danger-soft hover:text-danger"
          >
            {t("account.signOut")}
          </Button>
          <Button
            variant="ghost"
            leadingIcon={<ShieldOff aria-hidden className="h-4 w-4" />}
            loading={signingOutAll}
            disabled={signingOut}
            onClick={handleSignOutAll}
            className="mt-2 w-full justify-start text-ink-muted hover:bg-surface-hover hover:text-ink"
          >
            {t("account.signOutAll")}
          </Button>
        </div>
        <p className="mt-4 text-center text-[11px] leading-4 text-ink-muted">
          {t("account.demoNote")}
        </p>
      </DialogContent>
    </Dialog>
  );
}
