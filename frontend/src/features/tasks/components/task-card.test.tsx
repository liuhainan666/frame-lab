import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { TaskCard } from "@/features/tasks/components/task-card";
import type { TaskSummary } from "@/features/tasks/types";
import { API_BASE } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";
import {
  failedTask,
  queuedTask,
  resumingTask,
  retryingTask,
  succeededTask,
  videoSucceededTask
} from "@/test/fixtures/tasks";
import { renderWithProviders } from "@/test/utils";

function mockResult() {
  server.use(
    http.get(`${API_BASE}/api/tasks/:id/result`, () =>
      new HttpResponse("jpeg", { headers: { "Content-Type": "image/jpeg" } })
    )
  );
}

describe("任务卡", () => {
  it("排队任务显示等待工作线程", () => {
    renderWithProviders(<TaskCard task={queuedTask} onOpen={() => {}} />);
    expect(screen.getByTestId("task-status")).toHaveTextContent("排队中");
    expect(screen.getByText("等待工作线程")).toBeInTheDocument();
  });

  it("恢复中的任务显示正在恢复且不显示失败", () => {
    renderWithProviders(<TaskCard task={resumingTask} onOpen={() => {}} />);
    expect(screen.getByTestId("task-status")).toHaveTextContent("正在恢复");
    expect(screen.getByText("服务重启，将从断点继续")).toBeInTheDocument();
    expect(screen.getByText("模拟生成")).toBeInTheDocument();
  });

  it("等待重试显示尝试次数、阶段与最近原因", () => {
    renderWithProviders(<TaskCard task={retryingTask} onOpen={() => {}} />);
    expect(screen.getByTestId("task-status")).toHaveTextContent("等待重试");
    expect(screen.getByText("模拟生成")).toBeInTheDocument();
    expect(screen.getByText("模拟生成失败")).toBeInTheDocument();
    expect(screen.getByText(/已尝试 1\/3 次/)).toBeInTheDocument();
    expect(screen.queryByText("失败")).not.toBeInTheDocument();
  });

  it("nextRetryAt 到期也不自行推导为处理中", async () => {
    const overdue: TaskSummary = {
      ...retryingTask,
      retry: { ...retryingTask.retry, nextRetryAt: "2020-01-01T00:00:00.000Z" }
    };
    renderWithProviders(<TaskCard task={overdue} onOpen={() => {}} />);
    expect(screen.getByTestId("task-status")).toHaveTextContent("等待重试");
    await waitFor(() => expect(screen.getByTestId("task-status")).toHaveTextContent("等待重试"));
  });

  it("失败卡显示阶段、原因与尝试次数", () => {
    renderWithProviders(<TaskCard task={failedTask} onOpen={() => {}} />);
    expect(screen.getByTestId("task-status")).toHaveTextContent("失败");
    expect(screen.getByText("模拟生成")).toBeInTheDocument();
    expect(screen.getByText("生成失败，请稍后重试。")).toBeInTheDocument();
    expect(screen.getByText(/已尝试 3\/3 次/)).toBeInTheDocument();
  });

  it("成功卡展示结果图并可打开", async () => {
    mockResult();
    const onOpen = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<TaskCard task={succeededTask} onOpen={onOpen} />);

    expect(await screen.findByAltText("旅行明信片 制作结果")).toBeInTheDocument();
    expect(screen.getByTestId("task-status")).toHaveTextContent("已完成");

    await user.click(screen.getByTestId("task-card"));
    expect(onOpen).toHaveBeenCalledWith(succeededTask);
  });

  it("视频任务使用封面作为缩略并标记时长", async () => {
    server.use(
      http.get(`${API_BASE}/api/tasks/:id/result/poster`, () =>
        new HttpResponse("jpeg", { headers: { "Content-Type": "image/jpeg" } })
      )
    );
    renderWithProviders(<TaskCard task={videoSucceededTask} onOpen={() => {}} />);

    expect(await screen.findByAltText("城市漫游 制作结果")).toBeInTheDocument();
    expect(screen.getByText("15 秒")).toBeInTheDocument();
  });

  it("查询异常时只显示提示点，不改变任务状态", () => {
    renderWithProviders(<TaskCard task={retryingTask} onOpen={() => {}} stale />);
    expect(
      screen.getByLabelText("任务状态暂时无法更新，已保留最后一次结果")
    ).toBeInTheDocument();
    expect(screen.getByTestId("task-status")).toHaveTextContent("等待重试");
  });
});
