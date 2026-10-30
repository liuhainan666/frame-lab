import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/features/auth/store";
import { TaskDetailDialog } from "@/features/tasks/components/task-detail-dialog";
import { API_BASE, sessionFixture } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";
import { failedTaskDetail } from "@/test/fixtures/tasks";
import { renderWithProviders } from "@/test/utils";

beforeEach(() => {
  useAuthStore.getState().setSession(sessionFixture);
});

function mockDetail() {
  server.use(
    http.get(`${API_BASE}/api/tasks/:id`, () => HttpResponse.json(failedTaskDetail)),
    http.get(
      `${API_BASE}/api/assets/:id/content`,
      () => new HttpResponse("jpeg", { headers: { "Content-Type": "image/jpeg" } })
    )
  );
}

describe("任务详情弹层", () => {
  it("展示状态、失败原因、步骤与尝试历史", async () => {
    mockDetail();
    renderWithProviders(<TaskDetailDialog taskId={failedTaskDetail.id} open onOpenChange={() => {}} />);

    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(await screen.findByText("任务未能完成")).toBeInTheDocument();
    expect(screen.getByTestId("task-status")).toHaveTextContent("失败");
    expect(screen.getAllByTestId("attempt-list")).toHaveLength(2);
    expect(screen.getByText("已中断")).toBeInTheDocument();
    expect(screen.getByText("暂无尝试记录")).toBeInTheDocument();
    expect(screen.getAllByText(/GENERATION_FAILED/).length).toBeGreaterThan(0);
    expect(screen.getAllByText("生成失败，请稍后重试。").length).toBeGreaterThan(0);
    expect(screen.getAllByText("第 1 次尝试").length).toBeGreaterThan(0);
    expect(screen.getByText("第 2 次尝试")).toBeInTheDocument();
  });

  it("Esc 关闭时通知调用方", async () => {
    mockDetail();
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    renderWithProviders(
      <TaskDetailDialog taskId={failedTaskDetail.id} open onOpenChange={onOpenChange} />
    );

    await screen.findByRole("dialog");
    await user.keyboard("{Escape}");
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("请求失败显示错误与重试", async () => {
    server.use(
      http.get(`${API_BASE}/api/tasks/:id`, () =>
        HttpResponse.json(
          { type: "about:blank", title: "任务不存在", status: 404, code: "TASK_NOT_FOUND" },
          { status: 404, headers: { "Content-Type": "application/problem+json" } }
        )
      )
    );

    renderWithProviders(<TaskDetailDialog taskId="missing-task" open onOpenChange={() => {}} />);
    expect(await screen.findByText("任务暂时加载失败")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新加载" })).toBeInTheDocument();
  });
});
