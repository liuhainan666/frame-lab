"use client";

import { Images, Lock, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { useI18n } from "@/i18n/provider";
import { Button } from "@/shared/ui/button";
import { Skeleton } from "@/shared/ui/skeleton";

import { useTasks } from "@/features/tasks/queries";
import type { TaskSummary } from "@/features/tasks/types";
import { QueryErrorToast } from "@/features/tasks/components/query-error-toast";
import { ResultDialog } from "@/features/tasks/components/result-dialog";
import { TaskCard } from "@/features/tasks/components/task-card";
import { TaskDetailDialog } from "@/features/tasks/components/task-detail-dialog";

export function TaskHistoryPanel({
  templateId,
  authenticated,
  loginHref,
  highlightTaskId
}: {
  templateId: string;
  authenticated: boolean;
  loginHref: string;
  highlightTaskId: string | null;
}) {
  const { t } = useI18n();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [resultTask, setResultTask] = useState<TaskSummary | null>(null);
  const [resultOpen, setResultOpen] = useState(false);
  const query = useTasks({ templateId, page: 1, size: 20 });
  const pageData = query.data;
  const tasks = pageData?.items ?? [];
  const stale = query.isError && Boolean(pageData);

  function openTask(task: TaskSummary) {
    if (task.status === "SUCCEEDED" && task.result?.available) {
      setResultTask(task);
      setResultOpen(true);
      return;
    }
    setSelectedId(task.id);
    setDetailOpen(true);
  }

  if (!authenticated) {
    return (
      <section className="flex flex-col gap-4">
        <h2 className="h2">{t("studio.historyTitle")}</h2>
        <div className="flex min-h-[320px] flex-col items-center justify-center gap-4 rounded-[var(--radius-lg)] border border-dashed border-line-strong bg-surface/45 px-6 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-2 text-ink-muted">
            <Lock aria-hidden className="h-5 w-5" />
          </div>
          <div className="flex max-w-sm flex-col gap-2">
            <p className="h3 text-ink">{t("studio.anonymousHistory")}</p>
            <p className="small text-ink-secondary">{t("studio.anonymousHistoryDescription")}</p>
          </div>
          <Link href={loginHref}>
            <Button variant="secondary">{t("nav.login")}</Button>
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="h2">{t("studio.historyTitle")}</h2>
          {pageData ? (
            <span className="small text-ink-muted">
              {pageData.total === 1
                ? t("studio.historyCountOne", { count: pageData.total })
                : t("studio.historyCount", { count: pageData.total })}
            </span>
          ) : null}
        </div>
        <button
          type="button"
          aria-label={t("studio.refresh")}
          title={t("studio.refresh")}
          onClick={() => void query.refetch()}
          className="flex h-9 w-9 items-center justify-center rounded-full border border-line text-ink-secondary transition-colors hover:border-line-strong hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        >
          <RefreshCw
            aria-hidden
            className={query.isFetching ? "h-4 w-4 animate-spin" : "h-4 w-4"}
          />
        </button>
      </div>

      {stale ? (
        <QueryErrorToast
          message={t("history.stale")}
          onRetry={() => void query.refetch()}
        />
      ) : null}

      {query.isPending ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3" aria-busy>
          {Array.from({ length: 3 }).map((_, index) => (
            <Skeleton key={index} variant="card" />
          ))}
        </div>
      ) : null}

      {query.isError && !pageData ? (
        <div
          role="alert"
          className="flex min-h-[240px] flex-col items-center justify-center gap-3 rounded-[var(--radius-lg)] border border-danger/20 bg-danger-soft/30 px-6 text-center"
        >
          <p className="small text-ink-secondary">{t("studio.historyError")}</p>
          <Button variant="secondary" size="sm" onClick={() => void query.refetch()}>
            {t("common.retry")}
          </Button>
        </div>
      ) : null}

      {pageData && tasks.length === 0 ? (
        <div className="flex min-h-[320px] flex-col items-center justify-center gap-3 rounded-[var(--radius-lg)] border border-dashed border-line-strong bg-surface/45 px-6 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-2 text-ink-muted">
            <Images aria-hidden className="h-5 w-5" />
          </div>
          <p className="h3 text-ink">{t("studio.historyEmptyTitle")}</p>
          <p className="small text-ink-secondary">{t("studio.historyEmptyDescription")}</p>
        </div>
      ) : null}

      {tasks.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {tasks.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              onOpen={openTask}
              stale={stale}
              highlight={task.id === highlightTaskId}
            />
          ))}
        </div>
      ) : null}

      <TaskDetailDialog taskId={selectedId} open={detailOpen} onOpenChange={setDetailOpen} />
      <ResultDialog task={resultTask} open={resultOpen} onOpenChange={setResultOpen} />
    </section>
  );
}
