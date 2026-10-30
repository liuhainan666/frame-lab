import { refreshAccessToken } from "./client";
import { ApiError, networkError, toApiError } from "./errors";
import { getAccessToken } from "./auth-bridge";
import { env } from "@/shared/config/env";
import { DEFAULT_LOCALE } from "@/i18n/config";
import { currentLocaleOrDefault } from "@/i18n/locale-cookie";

async function blobRequest(path: string, allowRefresh: boolean, signal?: AbortSignal): Promise<Blob> {
  const headers = new Headers({
    Accept: "image/*",
    "Accept-Language": typeof document === "undefined" ? DEFAULT_LOCALE : currentLocaleOrDefault()
  });
  const token = getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${env.backendUrl}${path}`, { headers, signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    throw networkError();
  }

  if (response.ok) {
    return response.blob();
  }

  const error = await toApiError(response);
  if (response.status === 401 && error.code === "TOKEN_EXPIRED" && allowRefresh) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      return blobRequest(path, false, signal);
    }
  }
  throw error;
}

export function fetchBlob(path: string, signal?: AbortSignal): Promise<Blob> {
  return blobRequest(path, true, signal);
}

export { ApiError };
