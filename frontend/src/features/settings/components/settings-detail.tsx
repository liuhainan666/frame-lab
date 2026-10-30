"use client";

import { useI18n } from "@/i18n/provider";

import { SETTINGS_POINTS, type SettingsItemId } from "../settings-data";

export function SettingsDetail({ id }: { id: SettingsItemId }) {
  const { t } = useI18n();

  return (
    <div>
      {id === "billing" ? (
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
          {SETTINGS_POINTS[id].map((key) => (
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
    </div>
  );
}
