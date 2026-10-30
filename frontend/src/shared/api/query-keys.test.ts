import { describe, expect, it } from "vitest";

import { queryKeys } from "@/shared/api/query-keys";

describe("查询键", () => {
  it("任务与模板键包含语言，用户键包含 userId", () => {
    expect(queryKeys.templates("zh-CN", "travel")).toEqual(["templates", "zh-CN", "travel"]);
    expect(queryKeys.task(7, "en-US", "task-1")).toEqual(["task", 7, "en-US", "task-1"]);
    expect(queryKeys.tasks(7, "en-US", { scope: "active" })).toEqual([
      "tasks",
      7,
      "en-US",
      { scope: "active" }
    ]);
  });

  it("切换语言得到不同查询键", () => {
    expect(queryKeys.templates("zh-CN")).not.toEqual(queryKeys.templates("en-US"));
    expect(queryKeys.task(7, "zh-CN", "task-1")).not.toEqual(
      queryKeys.task(7, "en-US", "task-1")
    );
  });
});
