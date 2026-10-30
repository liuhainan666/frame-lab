"use client";

import { useCallback, useEffect, useState } from "react";

import { fetchBlob } from "@/shared/api/blob";

type ObjectUrlState = {
  url: string | null;
  size: number | null;
  status: "idle" | "loading" | "ready" | "error";
  error: unknown;
};

export function useObjectUrl(path: string | null | undefined, enabled = true) {
  const [state, setState] = useState<ObjectUrlState>({
    url: null,
    size: null,
    status: "idle",
    error: null
  });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled || !path) {
      setState({ url: null, size: null, status: "idle", error: null });
      return;
    }
    const controller = new AbortController();
    let objectUrl: string | null = null;
    setState({ url: null, size: null, status: "loading", error: null });

    fetchBlob(path, controller.signal)
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        setState({ url: objectUrl, size: blob.size, status: "ready", error: null });
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }
        setState({ url: null, size: null, status: "error", error });
      });

    return () => {
      controller.abort();
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [path, enabled, attempt]);

  const retry = useCallback(() => setAttempt((value) => value + 1), []);

  return { ...state, retry };
}
