import { describe, expect, it } from "vitest";

import { taskListPollInterval, taskPollInterval } from "./polling";
import { failedTask, queuedTask, retryingTask, succeededTask, taskPage } from "@/test/fixtures/tasks";

describe("任务轮询间隔", () => {
  it("按单任务状态返回间隔", () => {
    expect(taskPollInterval("RUNNING")).toBe(1500);
    expect(taskPollInterval("QUEUED")).toBe(2500);
    expect(taskPollInterval("RETRYING")).toBe(2500);
    expect(taskPollInterval("SUCCEEDED")).toBe(false);
    expect(taskPollInterval("FAILED")).toBe(false);
  });

  it("列表存在活跃任务时轮询，全部终态后停止", () => {
    expect(taskListPollInterval(undefined)).toBe(false);
    expect(taskListPollInterval(taskPage([]))).toBe(false);
    expect(taskListPollInterval(taskPage([queuedTask, failedTask]))).toBe(2500);
    expect(taskListPollInterval(taskPage([retryingTask]))).toBe(2500);
    expect(taskListPollInterval(taskPage([queuedTask, succeededTask, failedTask]))).toBe(2500);
    expect(taskListPollInterval(taskPage([succeededTask, failedTask]))).toBe(false);
  });
});
