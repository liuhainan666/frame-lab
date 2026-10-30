import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";

import { useAuthStore } from "@/features/auth/store";
import { configureAuthBridge } from "@/shared/api/auth-bridge";
import { clearLocaleCookie } from "@/i18n/locale-cookie";

import { server } from "./msw/server";

beforeAll(() => {
  server.listen({ onUnhandledFrame: "error" });

  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false
    })
  });

  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;

  Element.prototype.scrollIntoView = () => {};
  Element.prototype.hasPointerCapture = () => false;
  Element.prototype.setPointerCapture = () => {};
  Element.prototype.releasePointerCapture = () => {};
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  clearLocaleCookie();
  document.documentElement.lang = "zh-CN";
  useAuthStore.setState({
    status: "unknown",
    accessToken: null,
    user: null,
    expiresAt: null
  });
  configureAuthBridge({
    getAccessToken: () => useAuthStore.getState().accessToken,
    onSessionRefreshed: (session) => useAuthStore.getState().setSession(session),
    onSessionExpired: () => useAuthStore.getState().clearSession()
  });
});

afterAll(() => {
  server.close();
});
