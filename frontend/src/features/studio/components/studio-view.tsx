"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, FileWarning, LogIn, RotateCw } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type MouseEvent } from "react";

import { useAuthStore } from "@/features/auth/store";
import { useTemplate } from "@/features/templates/queries";
import { formatFileSize } from "@/i18n/format";
import { useI18n } from "@/i18n/provider";
import { isApiError } from "@/shared/api/errors";
import { backendUrl } from "@/shared/api/client";
import { cn } from "@/shared/lib/cn";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorState } from "@/shared/ui/error-state";
import { Skeleton } from "@/shared/ui/skeleton";
import { useToast } from "@/shared/ui/toast";

import { submitTask } from "@/features/tasks/api";
import { deleteAsset, uploadAsset } from "../api";
import type { SlotError, UploadSlotState } from "../types";
import { TaskHistoryPanel } from "./task-history-panel";
import { UploadSlot, typeLabel } from "./upload-slot";

function emptySlot(index: number): UploadSlotState {
  return {
    index,
    status: "empty",
    file: null,
    previewUrl: null,
    asset: null,
    progress: 0,
    error: null,
    errorDetail: null
  };
}

function createSlots(count: number): UploadSlotState[] {
  return Array.from({ length: count }, (_, index) => emptySlot(index + 1));
}

function StudioBackLink({
  href,
  onNavigate
}: {
  href: string;
  onNavigate?: (event: MouseEvent) => void;
}) {
  const { t } = useI18n();
  return (
    <Link
      href={href}
      onClick={onNavigate}
      className="inline-flex items-center gap-1.5 text-[13px] text-ink-secondary transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
    >
      <ArrowLeft aria-hidden className="h-3.5 w-3.5" />
      {t("studio.back")}
    </Link>
  );
}

function SubmitButton({
  ready,
  submitting,
  failed,
  onStart,
  onLogin,
  className
}: {
  ready: boolean;
  submitting: boolean;
  failed: boolean;
  onStart: () => void;
  onLogin: () => void;
  className?: string;
}) {
  const { t } = useI18n();
  const authenticated = useAuthStore((state) => state.status === "authenticated");

  if (!authenticated) {
    return (
      <button
        type="button"
        onClick={onLogin}
        className={cn(
          "flex h-12 w-full items-center justify-center gap-2 rounded-[14px] bg-accent px-6 text-[14px] font-semibold text-accent-ink transition-colors hover:bg-accent-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg",
          className
        )}
      >
        <LogIn aria-hidden className="h-4 w-4" />
        {t("studio.loginToStart")}
      </button>
    );
  }

  const inner = submitting
    ? t("studio.submitting")
    : failed
      ? t("studio.retrySubmit")
      : t("studio.start");

  return (
    <button
      type="button"
      onClick={onStart}
      disabled={!ready || submitting}
      aria-busy={submitting || undefined}
      className={cn(
        "flex h-12 w-full items-center justify-center gap-2 rounded-[14px] px-6 text-[14px] font-semibold transition-[background-color,color,box-shadow,transform] duration-150",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg",
        ready && !submitting
          ? "bg-[linear-gradient(180deg,var(--accent-hover),var(--accent)_55%,var(--accent-pressed))] text-accent-ink shadow-[inset_0_1px_0_rgba(255,255,255,0.35)] hover:-translate-y-px hover:brightness-[1.03] active:translate-y-0"
          : "cursor-not-allowed bg-surface-2 text-ink-disabled",
        className
      )}
    >
      {submitting ? (
        <RotateCw aria-hidden className="h-4 w-4 animate-spin" />
      ) : (
        <ArrowRight aria-hidden className="h-4 w-4" />
      )}
      {inner}
    </button>
  );
}

export function StudioView({
  templateId,
  backHref = "/"
}: {
  templateId: string;
  backHref?: string;
}) {
  const { t, locale } = useI18n();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const templateQuery = useTemplate(templateId);
  const template = templateQuery.data;
  const authenticated = useAuthStore((state) => state.status === "authenticated");
  const loginHref = `/login?returnTo=${encodeURIComponent(`/templates/${templateId}`)}`;

  const [slots, setSlots] = useState<UploadSlotState[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitFailed, setSubmitFailed] = useState(false);
  const [highlightTaskId, setHighlightTaskId] = useState<string | null>(null);
  const initializedFor = useRef<string | null>(null);
  const controllers = useRef(new Map<number, AbortController>());
  const slotsRef = useRef<UploadSlotState[]>([]);
  const intentRef = useRef<{ signature: string; clientRequestId: string } | null>(null);
  const highlightTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    slotsRef.current = slots;
  }, [slots]);

  useEffect(() => {
    if (template && initializedFor.current !== template.id) {
      initializedFor.current = template.id;
      setSlots(createSlots(template.requiredImageCount));
    }
  }, [template]);

  useEffect(() => {
    const activeControllers = controllers.current;
    return () => {
      activeControllers.forEach((controller) => controller.abort());
      slotsRef.current.forEach((slot) => {
        if (slot.previewUrl) {
          URL.revokeObjectURL(slot.previewUrl);
        }
      });
      if (highlightTimer.current) {
        clearTimeout(highlightTimer.current);
      }
    };
  }, []);

  const filled = slots.filter((slot) => slot.asset !== null).length;
  const total = template?.requiredImageCount ?? 0;
  const allUploaded = total > 0 && filled === total && slots.every((slot) => slot.status === "uploaded");
  const missing = Math.max(0, total - filled);
  const dirty = slots.some((slot) => slot.asset !== null || slot.status === "uploading") || submitting;

  useEffect(() => {
    if (!dirty) {
      return;
    }
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  function patchSlot(index: number, patch: Partial<UploadSlotState>) {
    setSlots((current) =>
      current.map((slot) => (slot.index === index ? { ...slot, ...patch } : slot))
    );
  }

  async function handleSelect(index: number, file: File) {
    if (!template || submitting) {
      return;
    }
    const slot = slots.find((item) => item.index === index);
    if (!slot) {
      return;
    }

    let validation: SlotError = null;
    if (!template.acceptedMimeTypes.includes(file.type)) {
      validation = "type";
    } else if (file.size > template.maxFileSizeBytes) {
      validation = "size";
    }
    if (validation) {
      toast({
        title:
          validation === "type"
            ? t("studio.errorType", {
                index,
                types: template.acceptedMimeTypes.map(typeLabel).join(" / ")
              })
            : t("studio.errorSize", {
                index,
                size: formatFileSize(template.maxFileSizeBytes, locale)
              }),
        tone: "error"
      });
      patchSlot(index, { error: validation, errorDetail: null });
      return;
    }

    controllers.current.get(index)?.abort();
    const controller = new AbortController();
    controllers.current.set(index, controller);

    if (slot.previewUrl) {
      URL.revokeObjectURL(slot.previewUrl);
    }
    if (slot.asset) {
      void deleteAsset(slot.asset.id).catch(() => undefined);
    }

    const previewUrl = URL.createObjectURL(file);
    setSubmitFailed(false);
    patchSlot(index, {
      status: "uploading",
      file,
      previewUrl,
      asset: null,
      progress: 0,
      error: null,
      errorDetail: null
    });

    try {
      const asset = await uploadAsset(
        file,
        template.id,
        (percent) => patchSlot(index, { progress: percent }),
        controller.signal
      );
      if (controllers.current.get(index) !== controller) {
        return;
      }
      controllers.current.delete(index);
      patchSlot(index, { status: "uploaded", asset, progress: 100 });
    } catch (error) {
      if (controller.signal.aborted) {
        return;
      }
      if (controllers.current.get(index) === controller) {
        controllers.current.delete(index);
      }
      const detail = isApiError(error) ? error.detail ?? error.message : null;
      toast({ title: t("studio.errorUpload"), description: detail ?? undefined, tone: "error" });
      patchSlot(index, { status: "error", error: "upload", errorDetail: detail });
    }
  }

  function handleRemove(index: number) {
    const slot = slots.find((item) => item.index === index);
    if (!slot) {
      return;
    }
    controllers.current.get(index)?.abort();
    controllers.current.delete(index);
    if (slot.asset) {
      void deleteAsset(slot.asset.id).catch(() => undefined);
    }
    if (slot.previewUrl) {
      URL.revokeObjectURL(slot.previewUrl);
    }
    setSubmitFailed(false);
    patchSlot(index, emptySlot(index));
  }

  async function handleSubmit() {
    if (!template || !allUploaded || submitting) {
      return;
    }
    const assetIds = slots.map((slot) => slot.asset?.id ?? "");
    const signature = `${template.id}:${assetIds.join(",")}`;
    if (!intentRef.current || intentRef.current.signature !== signature) {
      intentRef.current = { signature, clientRequestId: crypto.randomUUID() };
    }
    const intent = intentRef.current;
    setSubmitting(true);
    setSubmitFailed(false);

    try {
      const task = await submitTask({
        templateId: template.id,
        assetIds,
        clientRequestId: intent.clientRequestId
      });
      intentRef.current = null;
      setHighlightTaskId(task.id);
      if (highlightTimer.current) {
        clearTimeout(highlightTimer.current);
      }
      highlightTimer.current = setTimeout(() => setHighlightTaskId(null), 600);
      controllers.current.forEach((controller) => controller.abort());
      controllers.current.clear();
      slots.forEach((slot) => {
        if (slot.previewUrl) {
          URL.revokeObjectURL(slot.previewUrl);
        }
      });
      setSlots(createSlots(template.requiredImageCount));
      toast({ title: t("studio.toastCreated"), tone: "success" });
      void queryClient.invalidateQueries({ queryKey: ["tasks"] });
    } catch (error) {
      const detail = isApiError(error) ? error.detail ?? error.message : null;
      setSubmitFailed(true);
      toast({ title: t("studio.submitError"), description: detail ?? undefined, tone: "error" });
      if (isApiError(error) && error.code === "IDEMPOTENCY_KEY_REUSED") {
        intentRef.current = null;
      }
    } finally {
      setSubmitting(false);
    }
  }

  function handleBack(event: MouseEvent) {
    if (dirty && !window.confirm(t("studio.leaveConfirm"))) {
      event.preventDefault();
    }
  }

  if (templateQuery.isPending && !template) {
    return (
      <div>
        <StudioBackLink href={backHref} />
        <div className="mt-6 grid items-start gap-8 lg:grid-cols-[380px_minmax(0,1fr)] xl:grid-cols-[400px_minmax(0,1fr)]">
          <div className="flex flex-col gap-4">
            <Skeleton className="aspect-[4/3] w-full rounded-[16px]" />
            <Skeleton className="h-7 w-2/3" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-[180px] w-full rounded-[16px]" />
          </div>
          <div className="flex flex-col gap-4">
            <Skeleton className="h-7 w-40" />
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
              <Skeleton variant="card" />
              <Skeleton variant="card" />
              <Skeleton variant="card" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (templateQuery.isError && !template) {
    const notFound = isApiError(templateQuery.error) && templateQuery.error.status === 404;
    return (
      <div>
        <StudioBackLink href={backHref} />
        <div className="mt-8">
          {notFound ? (
            <EmptyState
              icon={<FileWarning aria-hidden className="h-5 w-5" />}
              title={t("studio.templateMissing")}
              action={
                <Link href="/">
                  <Button variant="secondary">{t("studio.backToTemplates")}</Button>
                </Link>
              }
            />
          ) : (
            <ErrorState
              title={t("studio.templateError")}
              retryLabel={t("errors.reload")}
              onRetry={() => void templateQuery.refetch()}
            />
          )}
        </div>
      </div>
    );
  }

  if (!template) {
    return null;
  }

  const mimeLabel = template.acceptedMimeTypes.map(typeLabel).join(" / ");
  const sizeLabel = formatFileSize(template.maxFileSizeBytes, locale);
  const uploadGridClass =
    total === 1
      ? "grid max-w-[220px] grid-cols-1 gap-3"
      : total === 3
        ? "grid grid-cols-2 gap-3"
        : "grid grid-cols-2 gap-3";

  return (
    <div className="pb-16 lg:pb-0">
      <StudioBackLink href={backHref} onNavigate={handleBack} />

      <div className="mt-6 grid items-start gap-8 lg:grid-cols-[380px_minmax(0,1fr)] xl:grid-cols-[400px_minmax(0,1fr)]">
        <aside className="flex min-w-0 flex-col gap-5 lg:sticky lg:top-[92px]">
          <div
            className="relative overflow-hidden rounded-[16px] border border-line bg-surface"
            style={{ aspectRatio: `${template.canvas.width} / ${template.canvas.height}` }}
          >
            <img
              src={backendUrl(template.coverUrl)}
              alt={t("templates.coverAlt", { title: template.title })}
              className="h-full w-full object-cover"
            />
          </div>

          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="accent">{template.category.name}</Badge>
              <span className="mono text-[11px] text-ink-muted">
                {template.canvas.width} × {template.canvas.height}
              </span>
            </div>
            <h1 className="text-[28px] font-semibold leading-9 tracking-[-0.02em] text-ink">
              {template.title}
            </h1>
            <p className="line-clamp-3 text-[14px] leading-[1.7] text-ink-secondary">
              {template.description}
            </p>
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[12px] text-ink-muted">
              <span>{t("studio.requirementsPhotos", { count: total })}</span>
              <span aria-hidden>·</span>
              <span>{mimeLabel || t("studio.requirementsMime")}</span>
              <span aria-hidden>·</span>
              <span>{t("studio.requirementsSize", { size: sizeLabel })}</span>
            </div>
          </div>

          <div className="flex items-center justify-between gap-3">
            <h2 className="h3">{t("studio.uploadTitle")}</h2>
            <span
              className={cn(
                "mono text-[12px]",
                allUploaded ? "text-accent" : "text-ink-muted"
              )}
            >
              {t("studio.uploadCount", { filled, total })}
            </span>
          </div>

          <div className={uploadGridClass}>
            {slots.map((slot) => (
              <div key={slot.index} className={total === 3 && slot.index === 1 ? "col-span-2" : undefined}>
                <UploadSlot
                  index={slot.index}
                  state={slot}
                  maxSizeBytes={template.maxFileSizeBytes}
                  acceptedTypes={template.acceptedMimeTypes}
                  disabled={submitting}
                  locked={!authenticated}
                  loginHref={loginHref}
                  onSelect={(file) => void handleSelect(slot.index, file)}
                  onRemove={() => handleRemove(slot.index)}
                  onRetry={() => {
                    if (slot.file) {
                      void handleSelect(slot.index, slot.file);
                    }
                  }}
                />
              </div>
            ))}
          </div>

          {!authenticated ? (
            <p className="text-[12px] leading-[18px] text-ink-muted">{t("studio.loginHint")}</p>
          ) : null}

          <div className="hidden flex-col gap-2 lg:flex">
            <SubmitButton
              ready={allUploaded}
              submitting={submitting}
              failed={submitFailed}
              onStart={() => void handleSubmit()}
              onLogin={() => router.push(loginHref)}
            />
            {authenticated && !allUploaded && !submitting ? (
              <p className="text-[12px] text-ink-muted">
                {missing === 1
                  ? t("studio.missingOne", { count: missing })
                  : t("studio.missing", { count: missing })}
              </p>
            ) : null}
          </div>
        </aside>

        <div className="min-w-0">
          <TaskHistoryPanel
            templateId={template.id}
            authenticated={authenticated}
            loginHref={loginHref}
            highlightTaskId={highlightTaskId}
          />
        </div>
      </div>

      <div
        className="fixed inset-x-0 bottom-0 z-30 border-t border-line bg-[rgba(10,10,11,0.9)] px-4 pt-3 backdrop-blur-xl lg:hidden"
        style={{ paddingBottom: "calc(12px + env(safe-area-inset-bottom))" }}
      >
        <SubmitButton
          ready={allUploaded}
          submitting={submitting}
          failed={submitFailed}
          onStart={() => void handleSubmit()}
          onLogin={() => router.push(loginHref)}
        />
        {authenticated && !allUploaded && !submitting ? (
          <p className="mt-1.5 text-center text-[12px] text-ink-muted">
            {missing === 1
              ? t("studio.missingOne", { count: missing })
              : t("studio.missing", { count: missing })}
          </p>
        ) : null}
      </div>
    </div>
  );
}
