export const HISTORY_FILTERS = ["all", "active", "succeeded", "failed"] as const;

export type HistoryFilter = (typeof HISTORY_FILTERS)[number];

const API_STATUS: Record<HistoryFilter, string> = {
  all: "all",
  active: "active",
  succeeded: "SUCCEEDED",
  failed: "FAILED"
};

export function historyStatus(filter: HistoryFilter): string {
  return API_STATUS[filter];
}

export function historyHref(filter: HistoryFilter, page: number): string {
  const params = new URLSearchParams();
  if (filter !== "all") {
    params.set("status", filter);
  }
  if (page > 1) {
    params.set("page", String(page));
  }
  const query = params.toString();
  return query ? `/history?${query}` : "/history";
}
