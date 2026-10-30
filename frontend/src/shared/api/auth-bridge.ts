import type { SessionPayload } from "./types";

export interface AuthBridge {
  getAccessToken(): string | null;
  onSessionRefreshed(session: SessionPayload): void;
  onSessionExpired(): void;
}

const defaultBridge: AuthBridge = {
  getAccessToken: () => null,
  onSessionRefreshed: () => {},
  onSessionExpired: () => {}
};

let bridge: AuthBridge = defaultBridge;

export function configureAuthBridge(next: Partial<AuthBridge>): void {
  bridge = { ...bridge, ...next };
}

export function getAccessToken(): string | null {
  return bridge.getAccessToken();
}

export function notifySessionRefreshed(session: SessionPayload): void {
  bridge.onSessionRefreshed(session);
}

export function notifySessionExpired(): void {
  bridge.onSessionExpired();
}

export function resetAuthBridge(): void {
  bridge = defaultBridge;
}
