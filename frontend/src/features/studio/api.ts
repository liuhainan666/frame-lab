import { useAuthStore } from "@/features/auth/store";
import { currentLocaleOrDefault } from "@/i18n/locale-cookie";
import { apiFetch, refreshAccessToken } from "@/shared/api/client";
import { networkError, toApiError } from "@/shared/api/errors";
import { env } from "@/shared/config/env";

import type { AssetView } from "./types";

export function deleteAsset(assetId: string): Promise<void> {
  return apiFetch<void>(`/api/assets/${encodeURIComponent(assetId)}`, { method: "DELETE" });
}

export function uploadAsset(
  file: File,
  templateId: string,
  onProgress: (percent: number) => void,
  signal?: AbortSignal
): Promise<AssetView> {
  return new Promise((resolve, reject) => {
    let active: XMLHttpRequest | null = null;
    let settled = false;

    const cleanup = () => signal?.removeEventListener("abort", handleAbort);
    const finish = (callback: () => void) => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      callback();
    };
    const handleAbort = () => active?.abort();

    if (signal?.aborted) {
      reject(new DOMException("Upload aborted", "AbortError"));
      return;
    }
    signal?.addEventListener("abort", handleAbort, { once: true });

    const send = (allowRefresh: boolean) => {
      if (settled) {
        return;
      }
      const xhr = new XMLHttpRequest();
      active = xhr;
      xhr.open("POST", `${env.backendUrl}/api/assets`);
      xhr.setRequestHeader("Accept", "application/json");
      xhr.setRequestHeader("Accept-Language", currentLocaleOrDefault());
      const token = useAuthStore.getState().accessToken;
      if (token) {
        xhr.setRequestHeader("Authorization", `Bearer ${token}`);
      }

      xhr.upload.onprogress = (event) => {
        if (!settled && event.lengthComputable && event.total > 0) {
          onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)));
        }
      };

      xhr.onload = () => {
        void (async () => {
          if (settled) {
            return;
          }
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              const asset = JSON.parse(xhr.responseText) as AssetView;
              finish(() => resolve(asset));
            } catch {
              finish(() => reject(networkError()));
            }
            return;
          }
          const response = new Response(xhr.responseText, {
            status: xhr.status,
            statusText: xhr.statusText,
            headers: {
              "Content-Type": xhr.getResponseHeader("Content-Type") ?? "application/json"
            }
          });
          const error = await toApiError(response);
          if (xhr.status === 401 && allowRefresh) {
            const refreshed = await refreshAccessToken();
            if (refreshed) {
              send(false);
              return;
            }
          }
          finish(() => reject(error));
        })();
      };

      xhr.onerror = () => finish(() => reject(networkError()));
      xhr.onabort = () => finish(() => reject(new DOMException("Upload aborted", "AbortError")));

      const form = new FormData();
      form.append("file", file);
      form.append("templateId", templateId);
      xhr.send(form);
    };

    send(true);
  });
}
