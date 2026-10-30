"use client";

import { AlertTriangle, CheckCircle2, Info, X } from "lucide-react";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode
} from "react";

import { useI18n } from "@/i18n/provider";
import { cn } from "@/shared/lib/cn";

export type ToastTone = "success" | "error" | "info" | "warning";

export type ToastAction = {
  label: string;
  onClick: () => void;
};

export type ToastInput = {
  key?: string;
  title: string;
  description?: string;
  tone?: ToastTone;
  /** Milliseconds before auto-dismiss; null keeps the toast until closed. */
  duration?: number | null;
  action?: ToastAction;
};

type ToastItem = ToastInput & { id: number };

type ToastContextValue = {
  toast: (input: ToastInput) => number;
  dismiss: (handle: number | string) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);
const DEFAULT_DURATION_MS = 4000;
const ERROR_DURATION_MS = 6000;
const MAX_VISIBLE = 4;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const itemsRef = useRef<ToastItem[]>([]);
  const counter = useRef(0);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  useEffect(() => {
    itemsRef.current = items;
  }, [items]);

  const clearTimer = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const dismiss = useCallback(
    (handle: number | string) => {
      const matching = itemsRef.current.filter((item) =>
        typeof handle === "number" ? item.id === handle : item.key === handle
      );
      matching.forEach((item) => clearTimer(item.id));
      setItems((current) =>
        current.filter((item) =>
          typeof handle === "number" ? item.id !== handle : item.key !== handle
        )
      );
    },
    [clearTimer]
  );

  const toast = useCallback(
    (input: ToastInput) => {
      if (input.key) {
        const existing = itemsRef.current.filter((item) => item.key === input.key);
        existing.forEach((item) => clearTimer(item.id));
        setItems((current) => current.filter((item) => item.key !== input.key));
      }

      counter.current += 1;
      const id = counter.current;
      setItems((current) => {
        const next = [...current, { ...input, id }];
        const overflow = next.length - MAX_VISIBLE;
        return overflow > 0 ? next.slice(overflow) : next;
      });

      const duration =
        input.duration === null
          ? null
          : input.duration ?? (input.tone === "error" ? ERROR_DURATION_MS : DEFAULT_DURATION_MS);
      if (duration !== null) {
        timers.current.set(
          id,
          setTimeout(() => dismiss(id), duration)
        );
      }
      return id;
    },
    [clearTimer, dismiss]
  );

  useEffect(() => {
    const currentTimers = timers.current;
    return () => {
      currentTimers.forEach((timer) => clearTimeout(timer));
      currentTimers.clear();
    };
  }, []);

  const value = useMemo(() => ({ toast, dismiss }), [toast, dismiss]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <ToastViewport items={items} onDismiss={dismiss} />
    </ToastContext.Provider>
  );
}

function ToastViewport({
  items,
  onDismiss
}: {
  items: ToastItem[];
  onDismiss: (handle: number | string) => void;
}) {
  const { t } = useI18n();
  return (
    <div
      aria-live="polite"
      aria-atomic="false"
      className="pointer-events-none fixed inset-x-0 top-[76px] z-[80] flex justify-center px-4"
    >
      <div className="flex w-[min(420px,100%)] flex-col gap-2">
        {items.map((item) => {
          const tone = item.tone ?? "info";
          const Icon =
            tone === "success"
              ? CheckCircle2
              : tone === "error" || tone === "warning"
                ? AlertTriangle
                : Info;
          return (
            <div
              key={item.id}
              data-testid="toast"
              data-tone={tone}
              data-key={item.key}
              role={tone === "error" ? "alert" : "status"}
              className={cn(
                "pointer-events-auto flex items-start gap-3 rounded-[var(--radius-md)] border p-4 shadow-[var(--shadow-panel)]",
                "animate-[fade-in_var(--dur-base)_var(--ease-out)]",
                tone === "success" && "border-success/25 bg-success-soft text-success",
                tone === "error" && "border-danger/25 bg-danger-soft text-danger",
                tone === "warning" && "border-warning/25 bg-warning-soft text-warning",
                tone === "info" && "border-info/25 bg-info-soft text-info"
              )}
            >
              <Icon aria-hidden className="mt-0.5 h-4 w-4 shrink-0" />
              <div className="min-w-0 flex-1">
                <p className="body-strong text-ink">{item.title}</p>
                {item.description ? (
                  <p className="small mt-1 text-ink-secondary">{item.description}</p>
                ) : null}
                {item.action ? (
                  <button
                    type="button"
                    onClick={item.action.onClick}
                    className="mt-2 rounded-full border border-current px-3 py-1 text-[12px] font-medium transition-colors hover:bg-white/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
                  >
                    {item.action.label}
                  </button>
                ) : null}
              </div>
              <button
                type="button"
                aria-label={t("common.close")}
                onClick={() => onDismiss(item.key ?? item.id)}
                className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-black/20 hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
              >
                <X aria-hidden className="h-3.5 w-3.5" />
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return context;
}
