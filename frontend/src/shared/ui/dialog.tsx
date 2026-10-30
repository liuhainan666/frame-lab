"use client";

import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ReactNode } from "react";

import { useI18n } from "@/i18n/provider";
import { cn } from "@/shared/lib/cn";

export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogClose = DialogPrimitive.Close;

export function DialogContent({
  title,
  description,
  children,
  footer,
  size = "default",
  className
}: {
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  size?: "default" | "wide";
  className?: string;
}) {
  const { t } = useI18n();
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="dialog-overlay" />
      <DialogPrimitive.Content
        className={cn(
          "dialog-content",
          size === "wide"
            ? "w-[min(880px,calc(100vw-32px))]"
            : "w-[min(480px,calc(100vw-32px))]",
          className
        )}
      >
        <div className="flex items-start justify-between gap-6">
          <div className="flex flex-col gap-2">
            <DialogPrimitive.Title className="h2">{title}</DialogPrimitive.Title>
            {description ? (
              <DialogPrimitive.Description className="small text-ink-secondary">
                {description}
              </DialogPrimitive.Description>
            ) : null}
          </div>
          <DialogPrimitive.Close asChild>
            <button
              type="button"
              aria-label={t("common.close")}
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-surface-hover hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            >
              <X aria-hidden className="h-4 w-4" />
            </button>
          </DialogPrimitive.Close>
        </div>
        <div className="mt-5">{children}</div>
        {footer ? <div className="mt-6 flex justify-end gap-3">{footer}</div> : null}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}
