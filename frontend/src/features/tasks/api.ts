import { apiFetch } from "@/shared/api/client";

import type {
  TaskDetail,
  TaskListFilters,
  TaskPage,
  TaskSubmitRequest
} from "./types";

export function fetchTasks(filters: TaskListFilters, signal?: AbortSignal): Promise<TaskPage> {
  const params = new URLSearchParams();
  if (filters.status && filters.status !== "all") {
    params.set("status", filters.status);
  }
  if (filters.templateId) {
    params.set("templateId", filters.templateId);
  }
  if (filters.page) {
    params.set("page", String(filters.page));
  }
  if (filters.size) {
    params.set("size", String(filters.size));
  }
  const query = params.toString();
  return apiFetch<TaskPage>(`/api/tasks${query ? `?${query}` : ""}`, { signal });
}

export function fetchTask(taskId: string, signal?: AbortSignal): Promise<TaskDetail> {
  return apiFetch<TaskDetail>(`/api/tasks/${encodeURIComponent(taskId)}`, { signal });
}

export function submitTask(request: TaskSubmitRequest): Promise<TaskDetail> {
  return apiFetch<TaskDetail>("/api/tasks", {
    method: "POST",
    body: JSON.stringify(request)
  });
}
