import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/features/auth/store";
import { HistoryView } from "@/features/tasks/components/history-view";
import { API_BASE, sessionFixture } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";
import { failedTask, queuedTask, retryingTask, taskPage } from "@/test/fixtures/tasks";
import { renderWithProviders } from "@/test/utils";

const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/history"
}));

function mockTasks(onRequest?: (url: URL) => void) {
  server.use(
    http.get(`${API_BASE}/api/tasks`, ({ request }) => {
      const url = new URL(request.url);
      onRequest?.(url);
      if (url.searchParams.get("status") === "active") {
        return HttpResponse.json(taskPage([retryingTask]));
      }
      return HttpResponse.json(taskPage([retryingTask, failedTask]));
    })
  );
}

beforeEach(() => {
  useAuthStore.getState().setSession(sessionFixture);
  router.replace.mockReset();
});

describe("历史页", () => {
  it("按筛选请求并同步 URL，不导航到新路由", async () => {
    const user = userEvent.setup();
    const requested: string[] = [];
    mockTasks((url) => requested.push(url.searchParams.get("status") ?? "none"));
    renderWithProviders(<HistoryView initialFilter="all" initialPage={1} />);

    expect(await screen.findAllByTestId("task-card")).toHaveLength(2);
    expect(screen.getByText("2 个任务")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "进行中" }));

    await waitFor(() => expect(screen.getAllByTestId("task-card")).toHaveLength(1));
    expect(requested).toContain("active");
    expect(router.replace).toHaveBeenCalledWith("/history?status=active", { scroll: false });
    expect(screen.getByTestId("task-status")).toHaveTextContent("等待重试");
  });

  it("分页切换页码并写入 URL", async () => {
    const user = userEvent.setup();
    const pages: string[] = [];
    server.use(
      http.get(`${API_BASE}/api/tasks`, ({ request }) => {
        const url = new URL(request.url);
        pages.push(url.searchParams.get("page") ?? "1");
        return HttpResponse.json(
          taskPage([queuedTask], { page: 1, total: 40, totalPages: 2, hasNext: true })
        );
      })
    );

    renderWithProviders(<HistoryView initialFilter="all" initialPage={1} />);
    expect(await screen.findAllByTestId("task-card")).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: "下一页" }));
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/history?page=2", { scroll: false }));
    await waitFor(() => expect(pages).toContain("2"));
  });

  it("筛选无结果显示带清除操作的空态", async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API_BASE}/api/tasks`, () => HttpResponse.json(taskPage([]))));

    renderWithProviders(<HistoryView initialFilter="failed" initialPage={1} />);
    expect(await screen.findByText("这个筛选下没有任务")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "清除筛选" }));
    expect(router.replace).toHaveBeenCalledWith("/history", { scroll: false });
  });

  it("查询失败保留旧数据并在顶部提示", async () => {
    let fail = false;
    server.use(
      http.get(`${API_BASE}/api/tasks`, () => {
        if (fail) {
          return HttpResponse.json(
            { type: "about:blank", title: "服务不可用", status: 503, code: "SERVICE_UNAVAILABLE" },
            { status: 503, headers: { "Content-Type": "application/problem+json" } }
          );
        }
        return HttpResponse.json(taskPage([retryingTask]));
      })
    );

    const { queryClient } = renderWithProviders(
      <HistoryView initialFilter="all" initialPage={1} />
    );
    expect(await screen.findAllByTestId("task-card")).toHaveLength(1);

    fail = true;
    await queryClient.refetchQueries({ queryKey: ["tasks"] });

    const toast = await screen.findByTestId("toast");
    expect(toast).toHaveTextContent("任务状态暂时无法更新");
    expect(screen.getByTestId("task-status")).toHaveTextContent("等待重试");
  });
});
