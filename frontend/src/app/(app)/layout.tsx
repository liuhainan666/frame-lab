import type { ReactNode } from "react";

import { AppHeader } from "@/features/account/components/app-header";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <AppHeader />
      <main id="main" className="page-shell min-h-[calc(100dvh-68px)] pb-20 pt-8 md:pt-12">
        {children}
      </main>
    </>
  );
}
