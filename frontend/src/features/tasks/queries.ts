"use client";

import { useQuery } from "@tanstack/react-query";

import { useAuthStore } from "@/features/auth/store";
import { useI18n } from "@/i18n/provider";
import { queryKeys } from "@/shared/api/query-keys";

import { fetchTask, fetchTasks } from "./api";
import { taskListPollInterval, taskPollInterval } from "./polling";
import type { TaskListFilters } from "./types";

export function useTasks(filters: TaskListFilters) {
  const { locale } = useI18n();
  const userId = useAuthStore((state) => state.user?.id ?? null);

  return useQuery({
    queryKey: queryKeys.tasks(userId ?? 0, locale, filters),
    queryFn: ({ signal }) => fetchTasks(filters, signal),
    enabled: userId !== null,
    staleTime: 0,
    refetchOnWindowFocus: true,
    refetchInterval: (query) => taskListPollInterval(query.state.data)
  });
}

export function useTask(taskId: string | null, enabled: boolean) {
  const { locale } = useI18n();
  const userId = useAuthStore((state) => state.user?.id ?? null);

  return useQuery({
    queryKey: queryKeys.task(userId ?? 0, locale, taskId ?? ""),
    queryFn: ({ signal }) => fetchTask(taskId as string, signal),
    enabled: enabled && userId !== null && Boolean(taskId),
    staleTime: 0,
    refetchOnWindowFocus: true,
    refetchInterval: (query) => taskPollInterval(query.state.data?.status)
  });
}
