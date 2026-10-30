"use client";

import { CheckCircle2, ImagePlus, Lock, RefreshCw, RotateCw, X } from "lucide-react";
import Link from "next/link";
import { useRef, useState, type ChangeEvent, type DragEvent } from "react";

import { formatFileSize } from "@/i18n/format";
import { useI18n } from "@/i18n/provider";
import { cn } from "@/shared/lib/cn";

import type { UploadSlotState } from "../types";

export function UploadSlot({
  index,
  state,
  maxSizeBytes,
  acceptedTypes,
  disabled = false,
  locked = false,
  loginHref,
  onSelect,
  onRemove,
  onRetry
}: {
  index: number;
  state: UploadSlotState;
  maxSizeBytes: number;
  acceptedTypes: string[];
  disabled?: boolean;
  locked?: boolean;
  loginHref?: string;
  onSelect: (file: File) => void;
  onRemove: () => void;
  onRetry: () => void;
}) {
  const { t, locale } = useI18n();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const sizeLabel = formatFileSize(maxSizeBytes, locale);
  const stateLabel =
    state.status === "empty"
      ? t("studio.slotStateEmpty")
      : state.status === "uploading"
        ? t("studio.slotUploading")
        : state.status === "uploaded"
          ? t("studio.slotUploaded")
          : t("studio.slotStateError");

  function openPicker() {
    if (!disabled && !locked) {
      inputRef.current?.click();
    }
  }

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (file) {
      onSelect(file);
    }
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    if (disabled || locked) {
      return;
    }
    const file = event.dataTransfer.files?.[0];
    if (file) {
      onSelect(file);
    }
  }

  const hasPreview = Boolean(state.previewUrl);

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <div
        data-testid="upload-slot"
        data-state={state.status}
        data-index={index}
        role="group"
        aria-label={t("studio.slotLabel", { index, state: stateLabel })}
        onDragOver={(event) => {
          if (disabled || locked) {
            return;
          }
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        className={cn(
          "group relative aspect-[4/3] overflow-hidden rounded-[14px] border bg-surface transition-colors",
          state.status === "empty" && "border-dashed border-line-strong",
          state.status === "empty" && !locked && "hover:border-accent-border hover:bg-accent-soft/30",
          state.status !== "empty" && "border-line",
          state.status === "error" && "border-danger/45",
          dragging && "border-accent-border ring-2 ring-accent/25"
        )}
      >
        <input
          ref={inputRef}
          type="file"
          accept={acceptedTypes.join(",")}
          className="sr-only"
          tabIndex={-1}
          aria-label={t("studio.chooseFile", { index })}
          onChange={handleChange}
        />

        <span className="mono absolute left-2 top-2 z-10 rounded bg-black/45 px-1.5 py-0.5 text-[10px] leading-4 text-white/75">
          {String(index).padStart(2, "0")}
        </span>

        {locked ? (
          <Link
            href={loginHref ?? "/login"}
            className="flex h-full w-full flex-col items-center justify-center gap-2 px-3 text-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          >
            <Lock aria-hidden className="h-5 w-5 text-ink-muted" />
            <span className="text-[13px] font-medium text-ink-secondary">{t("studio.slotLogin")}</span>
            <span className="text-[11px] text-ink-muted">{t("studio.slotHint", { size: sizeLabel })}</span>
          </Link>
        ) : hasPreview ? (
          <>
            <img
              src={state.previewUrl ?? undefined}
              alt={t("studio.slotLabel", { index, state: stateLabel })}
              className={cn(
                "h-full w-full object-cover transition-opacity",
                state.status === "uploading" && "opacity-60",
                state.status === "error" && "opacity-40"
              )}
            />

            {state.status === "uploaded" ? (
              <span className="absolute bottom-9 right-2 z-10 flex h-5 w-5 items-center justify-center rounded-full bg-success-soft text-success">
                <CheckCircle2 aria-hidden className="h-3.5 w-3.5" />
              </span>
            ) : null}

            {state.status === "uploading" ? (
              <div
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={state.progress}
                aria-label={t("studio.slotUploading")}
                className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-1.5 bg-bg/50"
              >
                <div className="relative h-10 w-10">
                  <svg viewBox="0 0 36 36" className="h-10 w-10 -rotate-90">
                    <circle
                      cx="18"
                      cy="18"
                      r="15"
                      fill="none"
                      stroke="var(--border-strong)"
                      strokeWidth="2.5"
                    />
                    <circle
                      cx="18"
                      cy="18"
                      r="15"
                      fill="none"
                      stroke="var(--accent)"
                      strokeWidth="2.5"
                      strokeLinecap="round"
                      strokeDasharray={`${(state.progress / 100) * 94.25} 94.25`}
                    />
                  </svg>
                  <span className="mono absolute inset-0 flex items-center justify-center text-[10px] text-ink">
                    {state.progress}%
                  </span>
                </div>
                <span className="text-[11px] text-ink-secondary">{t("studio.slotUploading")}</span>
              </div>
            ) : null}

            {state.status === "error" ? (
              <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 bg-bg/35">
                <button
                  type="button"
                  onClick={onRetry}
                  aria-label={t("studio.retrySlot", { index })}
                  className="flex items-center gap-1.5 rounded-full border border-danger/30 bg-danger-soft px-3 py-1.5 text-[12px] font-medium text-danger transition-colors hover:border-danger/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-danger"
                >
                  <RotateCw aria-hidden className="h-3.5 w-3.5" />
                  {t("common.retry")}
                </button>
              </div>
            ) : null}

            {state.file && state.status !== "uploading" ? (
              <div className="absolute inset-x-0 bottom-0 z-10 flex items-center justify-between gap-2 bg-gradient-to-t from-black/70 to-transparent px-2 pb-1.5 pt-4 text-white/75">
                <span className="truncate text-[10px]">{state.file.name}</span>
                <span className="mono shrink-0 text-[10px]">{formatFileSize(state.file.size, locale)}</span>
              </div>
            ) : null}

            {state.status !== "uploading" ? (
              <div className="absolute right-1.5 top-1.5 z-20 flex gap-1 opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100">
                <button
                  type="button"
                  onClick={openPicker}
                  disabled={disabled}
                  aria-label={t("studio.replaceSlot", { index })}
                  title={t("common.replace")}
                  className="flex h-8 w-8 items-center justify-center rounded-full bg-black/55 text-white/80 backdrop-blur transition-colors hover:bg-black/75 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <RefreshCw aria-hidden className="h-3.5 w-3.5" />
                </button>
                <button
                  type="button"
                  onClick={onRemove}
                  disabled={disabled}
                  aria-label={t("studio.removeSlot", { index })}
                  title={t("common.remove")}
                  className="flex h-8 w-8 items-center justify-center rounded-full bg-black/55 text-white/80 backdrop-blur transition-colors hover:bg-danger/70 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-danger disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <X aria-hidden className="h-3.5 w-3.5" />
                </button>
              </div>
            ) : null}
          </>
        ) : (
          <button
            type="button"
            onClick={openPicker}
            disabled={disabled}
            className="flex h-full w-full flex-col items-center justify-center gap-1.5 px-3 text-center transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed"
          >
            <ImagePlus
              aria-hidden
              className="h-6 w-6 text-ink-muted transition-colors group-hover:text-accent"
            />
            <span className="text-[13px] font-medium text-ink-secondary">{t("studio.slotEmpty")}</span>
            <span className="text-[11px] text-ink-muted">{t("studio.slotHint", { size: sizeLabel })}</span>
          </button>
        )}
      </div>
    </div>
  );
}

export function typeLabel(mime: string): string {
  if (mime === "image/jpeg") {
    return "JPG";
  }
  if (mime === "image/png") {
    return "PNG";
  }
  return mime.replace("image/", "").toUpperCase();
}
