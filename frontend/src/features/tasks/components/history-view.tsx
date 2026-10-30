"use client";

import { Images } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

import { useI18n } from "@/i18n/provider";
import { Button } from "@/shared/ui/button";
import { Chip } from "@/shared/ui/chip";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorState } from "@/shared/ui/error-state";
import { Pagination } from "@/shared/ui/pagination";

import { useTasks } from "../queries";
import { HISTORY_FILTERS, historyHref, historyStatus, type HistoryFilter } from "../filters";
import type { TaskSummary } from "../types";
import { QueryErrorBanner } from "./query-error-banner";
import { ResultDialog } from "./result-dialog";
import { TaskCard } from "./task-card";
import { TaskDetailDialog } from "./task-detail-dialog";
import { TaskGridSkeleton } from "./task-grid-skeleton";

export function HistoryView({
  initialFilter,
  initialPage
}: {
  initialFilter: HistoryFilter;
  initialPage: number;
}) {
  const { t } = useI18n();
  const router = useRouter();
  const [filter, setFilter] = useState<HistoryFilter>(initialFilter);
  const [page, setPage] = useState(initialPage);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [resultTask, setResultTask] = useState<TaskSummary | null>(null);
  const [resultOpen, setResultOpen] = useState(false);

  useEffect(() => {
    setFilter(initialFilter);
    setPage(initialPage);
  }, [initialFilter, initialPage]);

  const filters = useMemo(
    () => ({ status: historyStatus(filter), page, size: 20 }),
    [filter, page]
  );
  const query = useTasks(filters);
  const pageData = query.data;
  const tasks = pageData?.items ?? [];
  const hasCached = Boolean(pageData);
  const stale = query.isError && hasCached;

  function navigate(nextFilter: HistoryFilter, nextPage: number) {
    setFilter(nextFilter);
    setPage(nextPage);
    router.replace(historyHref(nextFilter, nextPage), { scroll: false });
  }

  function selectFilter(next: HistoryFilter) {
    if (next !== filter) {
      navigate(next, 1);
    }
  }

  function openTask(task: TaskSummary) {
    if (task.status === "SUCCEEDED" && task.result?.available) {
      setResultTask(task);
      setResultOpen(true);
      return;
    }
    setSelectedId(task.id);
    setDetailOpen(true);
  }

  return (
    <div>
      <header className="flex flex-wrap items-end justify-between gap-6">
        <div className="max-w-[620px]">
          <p className="eyebrow text-accent">{t("history.eyebrow")}</p>
          <h1 className="display mt-4">{t("history.title")}</h1>
          <p className="mt-4 max-w-[560px] text-[14px] leading-[1.8] text-ink-secondary">
            {t("history.description")}
          </p>
        </div>
        {pageData ? (
          <span className="mono pb-2 text-[12px] text-ink-muted">
            {pageData.total === 1
              ? t("history.totalOne", { count: pageData.total })
              : t("history.total", { count: pageData.total })}
          </span>
        ) : null}
      </header>

      <div role="group" aria-label={t("history.filtersLabel")} className="mt-8 flex flex-wrap gap-2">
        {HISTORY_FILTERS.map((value) => (
          <Chip key={value} selected={filter === value} onClick={() => selectFilter(value)}>
            {t(`history.filters.${value}`)}
          </Chip>
        ))}
      </div>

      {stale ? (
        <div className="mt-5">
          <QueryErrorBanner
            message={t("history.stale")}
            retrying={query.isFetching}
            onRetry={() => void query.refetch()}
          />
        </div>
      ) : null}

      <div className="mt-6">
        {query.isPending ? <TaskGridSkeleton count={4} /> : null}

        {query.isError && !hasCached ? (
          <ErrorState
            title={t("history.errorTitle")}
            description={t("history.errorDescription")}
            retryLabel={t("errors.reload")}
            onRetry={() => void query.refetch()}
          />
        ) : null}

        {hasCached && tasks.length === 0 ? (
          <EmptyState
            icon={<Images aria-hidden className="h-5 w-5" />}
            title={filter === "all" ? t("history.emptyTitle") : t("history.emptyFilteredTitle")}
            description={
              filter === "all"
                ? t("history.emptyDescription")
                : t("history.emptyFilteredDescription")
            }
            action={
              filter === "all" ? (
                <Link href="/">
                  <Button variant="secondary">{t("history.goTemplates")}</Button>
                </Link>
              ) : (
                <Button variant="secondary" onClick={() => selectFilter("all")}>
                  {t("common.clearFilters")}
                </Button>
              )
            }
          />
        ) : null}

        {tasks.length > 0 ? (
          <>
            <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {tasks.map((task) => (
                <TaskCard key={task.id} task={task} onOpen={openTask} stale={stale} />
              ))}
            </div>
            {pageData ? (
              <Pagination
                className="mt-10"
                page={pageData.page}
                totalPages={pageData.totalPages}
                onPage={(next) => navigate(filter, next)}
                prevLabel={t("history.prev")}
                nextLabel={t("history.next")}
                label={t("history.pagination")}
              />
            ) : null}
          </>
        ) : null}
      </div>

      <TaskDetailDialog taskId={selectedId} open={detailOpen} onOpenChange={setDetailOpen} />
      <ResultDialog task={resultTask} open={resultOpen} onOpenChange={setResultOpen} />
    </div>
  );
}
