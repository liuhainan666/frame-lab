import { currentLocaleOrDefault } from "@/i18n/locale-cookie";
import { DEFAULT_LOCALE } from "@/i18n/config";
import { env } from "@/shared/config/env";

import { getAccessToken, notifySessionExpired, notifySessionRefreshed } from "./auth-bridge";
import { ApiError, networkError, toApiError } from "./errors";
import type { SessionPayload } from "./types";

export type ApiRequestInit = RequestInit & {
  skipAuth?: boolean;
};

let refreshPromise: Promise<string | null> | null = null;

function isAuthPath(path: string): boolean {
  return path.startsWith("/api/auth/");
}

function localeHeader(): string {
  return typeof document === "undefined" ? DEFAULT_LOCALE : currentLocaleOrDefault();
}

export async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function performRefresh(): Promise<string | null> {
  try {
    const response = await fetch(`${env.backendUrl}/api/auth/refresh`, {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
        "Accept-Language": localeHeader()
      }
    });
    if (!response.ok) {
      return null;
    }
    const session = (await response.json()) as SessionPayload;
    notifySessionRefreshed(session);
    return session.accessToken;
  } catch {
    return null;
  }
}

async function request<T>(path: string, init: ApiRequestInit, allowRefresh: boolean): Promise<T> {
  const headers = new Headers(init.headers);
  const token = init.skipAuth ? null : getAccessToken();
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  if (!headers.has("Accept-Language")) {
    headers.set("Accept-Language", localeHeader());
  }
  if (typeof init.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(`${env.backendUrl}${path}`, {
      ...init,
      headers,
      credentials: init.credentials ?? (isAuthPath(path) ? "include" : "same-origin")
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    throw networkError();
  }

  if (response.ok) {
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  const apiError = await toApiError(response);
  if (response.status === 401 && !isAuthPath(path)) {
    if (apiError.code === "TOKEN_EXPIRED" && allowRefresh) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        return request<T>(path, init, false);
      }
      notifySessionExpired();
      throw apiError;
    }
    if (token && apiError.code === "TOKEN_INVALID") {
      notifySessionExpired();
    }
  }
  throw apiError;
}

export function apiFetch<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  return request<T>(path, init, true);
}

export function backendUrl(path: string): string {
  return `${env.backendUrl}${path}`;
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

export { ApiError };
