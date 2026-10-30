import type { MessageKey } from "@/i18n/messages/zh-CN";

export type SettingsItemId = "billing" | "terms" | "privacy" | "subscription";

export const SETTINGS_ITEMS: ReadonlyArray<{
  id: SettingsItemId;
  label: MessageKey;
  title: MessageKey;
  body: MessageKey;
}> = [
  {
    id: "billing",
    label: "settings.billing",
    title: "settings.billingTitle",
    body: "settings.billingBody"
  },
  {
    id: "terms",
    label: "settings.terms",
    title: "settings.termsTitle",
    body: "settings.termsBody"
  },
  {
    id: "privacy",
    label: "settings.privacy",
    title: "settings.privacyTitle",
    body: "settings.privacyBody"
  },
  {
    id: "subscription",
    label: "settings.subscription",
    title: "settings.subscriptionTitle",
    body: "settings.subscriptionBody"
  }
];

export const SETTINGS_POINTS: Record<Exclude<SettingsItemId, "billing">, MessageKey[]> = {
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
