"use client";

import { AlertCircle, ChevronDown, Eye, EyeOff } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";

import { login } from "@/features/auth/api";
import { validateLogin, type LoginFieldErrors } from "@/features/auth/schemas";
import { useAuthStore } from "@/features/auth/store";
import { useI18n } from "@/i18n/provider";
import type { MessageKey } from "@/i18n/messages/zh-CN";
import { isApiError } from "@/shared/api/errors";
import type { TranslateFn } from "@/i18n/translate";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";

const DEMO_ACCOUNTS: Array<{
  email: string;
  password: string;
  roleKey: MessageKey;
}> = [
  { email: "demo@framelab.local", password: "Demo1234!", roleKey: "auth.demoUser" },
  { email: "demo2@framelab.local", password: "Demo1234!", roleKey: "auth.demoIsolation" }
];

function authErrorMessage(error: unknown, t: TranslateFn): string {
  if (!isApiError(error)) {
    return t("errors.generic");
  }
  if (error.status === 0 || error.code === "NETWORK_ERROR") {
    return t("auth.networkError");
  }
  switch (error.code) {
    case "INVALID_CREDENTIALS":
      return t("auth.invalidCredentials");
    case "RATE_LIMITED":
      return t("auth.rateLimited");
    default:
      return error.detail ?? t("errors.generic");
  }
}

export function LoginForm({ returnTo }: { returnTo: string }) {
  const { t } = useI18n();
  const router = useRouter();
  const status = useAuthStore((state) => state.status);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<LoginFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (status === "authenticated") {
      router.replace(returnTo);
    }
  }, [returnTo, router, status]);

  function fillDemo(nextEmail: string, nextPassword: string) {
    setEmail(nextEmail);
    setPassword(nextPassword);
    setFieldErrors({});
    setFormError(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validation = validateLogin({ email, password });
    if (validation.email || validation.password) {
      setFieldErrors({
        email: validation.email ? t("auth.invalidEmail") : undefined,
        password: validation.password ? t("auth.passwordRequired") : undefined
      });
      setFormError(null);
      return;
    }

    setSubmitting(true);
    setFormError(null);
    setFieldErrors({});
    try {
      await login({ email: email.trim(), password });
      router.replace(returnTo);
    } catch (error) {
      if (isApiError(error) && error.fieldErrors.length > 0) {
        const apiFieldErrors: LoginFieldErrors = {};
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === "email" || fieldError.field === "password") {
            apiFieldErrors[fieldError.field] = fieldError.message;
          }
        }
        setFieldErrors(apiFieldErrors);
      }
      setFormError(authErrorMessage(error, t));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="mt-8 flex flex-col gap-5">
      {formError ? (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-[var(--radius-sm)] border border-danger/25 bg-danger-soft px-3.5 py-3 text-[12px] leading-[18px] text-danger"
        >
          <AlertCircle aria-hidden className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{formError}</span>
        </div>
      ) : null}

      <Input
        label={t("auth.email")}
        type="email"
        inputMode="email"
        autoComplete="username"
        placeholder={t("auth.emailPlaceholder")}
        value={email}
        error={fieldErrors.email}
        onChange={(event) => {
          setEmail(event.target.value);
          if (fieldErrors.email) {
            setFieldErrors((current) => ({ ...current, email: undefined }));
          }
        }}
      />

      <Input
        label={t("auth.password")}
        type={showPassword ? "text" : "password"}
        autoComplete="current-password"
        placeholder={t("auth.passwordPlaceholder")}
        value={password}
        error={fieldErrors.password}
        onChange={(event) => {
          setPassword(event.target.value);
          if (fieldErrors.password) {
            setFieldErrors((current) => ({ ...current, password: undefined }));
          }
        }}
        endAdornment={
          <button
            type="button"
            aria-label={showPassword ? t("common.hidePassword") : t("common.showPassword")}
            onClick={() => setShowPassword((visible) => !visible)}
            className="flex h-8 w-8 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-surface-hover hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          >
            {showPassword ? (
              <EyeOff aria-hidden className="h-4 w-4" />
            ) : (
              <Eye aria-hidden className="h-4 w-4" />
            )}
          </button>
        }
      />

      <Button type="submit" size="lg" loading={submitting} className="mt-1 w-full">
        {submitting ? t("auth.submitting") : t("auth.submit")}
      </Button>

      <details className="group rounded-[var(--radius-md)] border border-line bg-surface/55">
        <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3.5 text-[13px] font-medium text-ink-secondary transition-colors hover:text-ink [&::-webkit-details-marker]:hidden">
          <span>{t("auth.demoTitle")}</span>
          <ChevronDown
            aria-hidden
            className="h-4 w-4 shrink-0 transition-transform duration-200 group-open:rotate-180"
          />
        </summary>
        <div className="flex flex-col gap-2 border-t border-line px-4 py-3">
          <p className="text-[12px] leading-[18px] text-ink-muted">{t("auth.demoHint")}</p>
          {DEMO_ACCOUNTS.map((account) => (
            <div
              key={account.email}
              className="flex items-center justify-between gap-3 rounded-[var(--radius-xs)] bg-bg-elevated/70 px-3 py-2"
            >
              <div className="min-w-0">
                <p className="mono truncate text-[11px] text-ink-secondary">{account.email}</p>
                <p className="mono mt-0.5 truncate text-[11px] text-ink-muted">
                  {account.password} · {t(account.roleKey)}
                </p>
              </div>
              <Button
                variant="ghost"
                size="sm"
                aria-label={`${t("common.fill")} ${account.email}`}
                onClick={() => fillDemo(account.email, account.password)}
              >
                {t("common.fill")}
              </Button>
            </div>
          ))}
        </div>
      </details>
    </form>
  );
}
