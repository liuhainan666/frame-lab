"use client";

import {
  ChevronRight,
  CreditCard,
  FileBadge,
  FileText,
  ShieldCheck
} from "lucide-react";
import { useState } from "react";

import { useI18n } from "@/i18n/provider";
import type { MessageKey } from "@/i18n/messages/zh-CN";
import { Dialog, DialogContent } from "@/shared/ui/dialog";

type SettingsItemId = "billing" | "terms" | "privacy" | "subscription";

const ITEMS: ReadonlyArray<{
  id: SettingsItemId;
  icon: typeof CreditCard;
  label: MessageKey;
  title: MessageKey;
  body: MessageKey;
}> = [
  {
    id: "billing",
    icon: CreditCard,
    label: "settings.billing",
    title: "settings.billingTitle",
    body: "settings.billingBody"
  },
  {
    id: "terms",
    icon: FileText,
    label: "settings.terms",
    title: "settings.termsTitle",
    body: "settings.termsBody"
  },
  {
    id: "privacy",
    icon: ShieldCheck,
    label: "settings.privacy",
    title: "settings.privacyTitle",
    body: "settings.privacyBody"
  },
  {
    id: "subscription",
    icon: FileBadge,
    label: "settings.subscription",
    title: "settings.subscriptionTitle",
    body: "settings.subscriptionBody"
  }
];

const POINTS: Record<Exclude<SettingsItemId, "billing">, MessageKey[]> = {
  terms: ["settings.termsPoint1", "settings.termsPoint2", "settings.termsPoint3"],
  privacy: [
    "settings.privacyPoint1",
    "settings.privacyPoint2",
    "settings.privacyPoint3",
    "settings.privacyPoint4"
  ],
  subscription: [
    "settings.subscriptionPoint1",
    "settings.subscriptionPoint2",
    "settings.subscriptionPoint3"
  ]
};

export function SettingsView() {
  const { t } = useI18n();
  const [selected, setSelected] = useState<SettingsItemId | null>(null);
  const current = selected ? ITEMS.find((item) => item.id === selected) : null;

  return (
    <div className="mx-auto w-full max-w-[860px]">
      <header>
        <p className="eyebrow text-accent">{t("settings.eyebrow")}</p>
        <h1 className="display mt-4">{t("settings.title")}</h1>
        <p className="mt-4 max-w-[620px] text-[14px] leading-[1.8] text-ink-secondary">
          {t("settings.description")}
        </p>
      </header>

      <section
        aria-label={t("settings.listLabel")}
        className="mt-8 overflow-hidden rounded-[24px] border border-line bg-bg-elevated/55 p-2 sm:p-3"
      >
        {ITEMS.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setSelected(item.id)}
              className="group flex min-h-[70px] w-full items-center gap-4 rounded-[18px] px-4 py-4 text-left transition-colors hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent sm:px-6"
            >
              <Icon
                aria-hidden
                strokeWidth={1.6}
                className="h-5 w-5 shrink-0 text-ink-secondary transition-colors group-hover:text-ink"
              />
              <span className="flex-1 text-[16px] font-medium text-ink sm:text-[17px]">
                {t(item.label)}
              </span>
              <ChevronRight
                aria-hidden
                strokeWidth={1.8}
                className="h-4 w-4 shrink-0 text-ink-muted transition-transform group-hover:translate-x-0.5 group-hover:text-ink"
              />
            </button>
          );
        })}
      </section>

      <Dialog open={selected !== null} onOpenChange={(open) => !open && setSelected(null)}>
        {current ? (
          <DialogContent title={t(current.title)} description={t(current.body)}>
            {current.id === "billing" ? (
              <dl className="flex flex-col gap-3 text-[13px]">
                {(
                  [
                    ["settings.billingPlanLabel", "settings.billingPlanValue"],
                    ["settings.billingPaymentLabel", "settings.billingPaymentValue"],
                    ["settings.billingHistoryLabel", "settings.billingHistoryValue"]
                  ] as const
                ).map(([label, value]) => (
                  <div
                    key={label}
                    className="flex items-center justify-between gap-4 border-b border-line pb-3 last:border-b-0 last:pb-0"
                  >
                    <dt className="text-ink-muted">{t(label)}</dt>
                    <dd className="text-ink-secondary">{t(value)}</dd>
                  </div>
                ))}
              </dl>
            ) : (
              <ul className="flex flex-col gap-3 text-[13px] leading-[1.7] text-ink-secondary">
                {POINTS[current.id].map((key) => (
                  <li key={key} className="flex gap-3">
                    <span aria-hidden className="mt-2 h-1 w-1 shrink-0 rounded-full bg-accent" />
                    {t(key)}
                  </li>
                ))}
              </ul>
            )}
            <p className="mt-5 inline-flex rounded-full border border-line bg-surface px-3 py-1 text-[11px] text-ink-muted">
              {t("settings.demoBadge")}
            </p>
          </DialogContent>
        ) : null}
      </Dialog>
    </div>
  );
}
