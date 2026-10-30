import { RequireAuth } from "@/features/auth/components/require-auth";
import { HISTORY_FILTERS, type HistoryFilter } from "@/features/tasks/filters";
import { HistoryView } from "@/features/tasks/components/history-view";

function parseFilter(value: string | string[] | undefined): HistoryFilter {
  const raw = Array.isArray(value) ? value[0] : value;
  return HISTORY_FILTERS.includes(raw as HistoryFilter) ? (raw as HistoryFilter) : "all";
}

function parsePage(value: string | string[] | undefined): number {
  const raw = Array.isArray(value) ? value[0] : value;
  const parsed = Number.parseInt(raw ?? "1", 10);
  return Number.isFinite(parsed) && parsed >= 1 ? parsed : 1;
}

export default async function HistoryPage({
  searchParams
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  return (
    <RequireAuth>
      <HistoryView initialFilter={parseFilter(params.status)} initialPage={parsePage(params.page)} />
    </RequireAuth>
  );
}
