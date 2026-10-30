import { apiFetch, refreshAccessToken } from "@/shared/api/client";
import type { SessionPayload } from "@/shared/api/types";

import type { LoginInput } from "./schemas";
import { useAuthStore } from "./store";

export function login(input: LoginInput): Promise<SessionPayload> {
  return apiFetch<SessionPayload>("/api/auth/login", {
    method: "POST",
    skipAuth: true,
    body: JSON.stringify(input)
  }).then((session) => {
    useAuthStore.getState().setSession(session);
    return session;
  });
}

export async function bootstrapSession(): Promise<void> {
  if (useAuthStore.getState().status !== "unknown") {
    return;
  }
  const token = await refreshAccessToken();
  if (!token) {
    useAuthStore.getState().setAnonymous();
  }
}

export async function logout(): Promise<void> {
  try {
    await apiFetch<void>("/api/auth/logout", { method: "POST", skipAuth: true });
  } finally {
    useAuthStore.getState().clearSession();
  }
}

export async function logoutAll(): Promise<void> {
  try {
    await apiFetch<void>("/api/auth/logout-all", { method: "POST" });
  } finally {
    useAuthStore.getState().clearSession();
  }
}
