"use client";

import { AlertTriangle } from "lucide-react";

import { useI18n } from "@/i18n/provider";
import { Button } from "@/shared/ui/button";
import { ErrorState } from "@/shared/ui/error-state";

export default function GlobalError({ reset }: { reset: () => void }) {
  const { t } = useI18n();
  return (
    <main id="main" className="page-shell flex min-h-dvh items-center justify-center py-16">
      <ErrorState
        title={t("errors.generic")}
        action={
          <Button
            variant="secondary"
            leadingIcon={<AlertTriangle aria-hidden className="h-4 w-4" />}
            onClick={reset}
          >
            {t("errors.reload")}
          </Button>
        }
      />
    </main>
  );
}
