import { create } from "zustand";

import { configureAuthBridge } from "@/shared/api/auth-bridge";
import type { SessionPayload, User } from "@/shared/api/types";

export type AuthStatus = "unknown" | "authenticated" | "anonymous";

export type AuthState = {
  status: AuthStatus;
  accessToken: string | null;
  user: User | null;
  expiresAt: number | null;
  setSession: (session: SessionPayload) => void;
  setAnonymous: () => void;
  clearSession: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  status: "unknown",
  accessToken: null,
  user: null,
  expiresAt: null,
  setSession: (session) =>
    set({
      status: "authenticated",
      accessToken: session.accessToken,
      user: session.user,
      expiresAt: Date.now() + session.expiresIn * 1000
    }),
  setAnonymous: () =>
    set({ status: "anonymous", accessToken: null, user: null, expiresAt: null }),
  clearSession: () =>
    set({ status: "anonymous", accessToken: null, user: null, expiresAt: null })
}));

function redirectToLogin(): void {
  if (typeof window === "undefined") {
    return;
  }
  if (window.location.pathname.startsWith("/login")) {
    return;
  }
  const returnTo = `${window.location.pathname}${window.location.search}`;
  window.location.assign(`/login?returnTo=${encodeURIComponent(returnTo)}`);
}

configureAuthBridge({
  getAccessToken: () => useAuthStore.getState().accessToken,
  onSessionRefreshed: (session) => useAuthStore.getState().setSession(session),
  onSessionExpired: () => {
    useAuthStore.getState().clearSession();
    redirectToLogin();
  }
});
