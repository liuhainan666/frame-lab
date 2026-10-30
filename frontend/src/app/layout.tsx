import type { Metadata } from "next";
import localFont from "next/font/local";
import { cookies } from "next/headers";

import { LOCALE_COOKIE, normalizeLocale } from "@/i18n/config";
import { t } from "@/i18n/translate";

import "./globals.css";
import { Providers } from "./providers";

const inter = localFont({
  src: "../assets/fonts/InterVariable.woff2",
  variable: "--font-inter",
  display: "swap",
  weight: "100 900"
});

const jetBrainsMono = localFont({
  src: "../assets/fonts/JetBrainsMono-Regular.woff2",
  variable: "--font-jetbrains",
  display: "swap",
  weight: "400"
});

export const metadata: Metadata = {
  title: {
    default: "FrameLab",
    template: "%s · FrameLab"
  },
  description: "FrameLab · 选择模板、上传素材，三步完成出图。"
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const cookieStore = await cookies();
  const locale = normalizeLocale(cookieStore.get(LOCALE_COOKIE)?.value);

  return (
    <html lang={locale} className={`${inter.variable} ${jetBrainsMono.variable}`}>
      <body>
        <a className="skip-link" href="#main">
          {t(locale, "app.skipToContent")}
        </a>
        <Providers initialLocale={locale}>{children}</Providers>
      </body>
    </html>
  );
}
