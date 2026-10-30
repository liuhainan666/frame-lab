"use client";

import { useEffect, useRef } from "react";

import { useI18n } from "@/i18n/provider";
import { useToast } from "@/shared/ui/toast";

const NOTICE_KEY = "tasks-stale";

export function QueryErrorToast({
  message,
  onRetry
}: {
  message: string;
  onRetry: () => void;
}) {
  const { t } = useI18n();
  const { toast, dismiss } = useToast();
  const retryRef = useRef(onRetry);
  retryRef.current = onRetry;

  useEffect(() => {
    toast({
      key: NOTICE_KEY,
      title: message,
      tone: "warning",
      duration: null,
      action: { label: t("common.retry"), onClick: () => retryRef.current() }
    });
    return () => dismiss(NOTICE_KEY);
  }, [dismiss, message, t, toast]);

  return null;
}
