"use client";

import { ChevronRight, CreditCard, FileBadge, FileText, ShieldCheck } from "lucide-react";

import { useI18n } from "@/i18n/provider";

import { SETTINGS_ITEMS, type SettingsItemId } from "../settings-data";

const ICONS = {
  billing: CreditCard,
  terms: FileText,
  privacy: ShieldCheck,
  subscription: FileBadge
} as const;

export function SettingsList({ onSelect }: { onSelect: (id: SettingsItemId) => void }) {
  const { t } = useI18n();

  return (
    <div className="flex flex-col">
      {SETTINGS_ITEMS.map((item) => {
        const Icon = ICONS[item.id];
        return (
          <button
            key={item.id}
            type="button"
            onClick={() => onSelect(item.id)}
            className="group flex min-h-[48px] w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          >
            <Icon
              aria-hidden
              strokeWidth={1.6}
              className="h-[18px] w-[18px] shrink-0 text-ink-secondary transition-colors group-hover:text-ink"
            />
            <span className="flex-1 text-[14px] font-medium text-ink">{t(item.label)}</span>
            <ChevronRight
              aria-hidden
              strokeWidth={1.8}
              className="h-4 w-4 shrink-0 text-ink-muted transition-transform group-hover:translate-x-0.5"
            />
          </button>
        );
      })}
    </div>
  );
}
