import { screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { ResultDialog } from "@/features/tasks/components/result-dialog";
import { API_BASE } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";
import { videoSucceededTask } from "@/test/fixtures/tasks";
import { renderWithProviders } from "@/test/utils";

describe("结果弹层", () => {
  it("视频结果渲染播放控件、封面与时长", async () => {
    server.use(
      http.get(
        `${API_BASE}/api/tasks/:id/result`,
        () => new HttpResponse(new Uint8Array([0, 0, 0, 20]), {
          headers: { "Content-Type": "video/mp4" }
        })
      ),
      http.get(
        `${API_BASE}/api/tasks/:id/result/poster`,
        () => new HttpResponse("jpeg", { headers: { "Content-Type": "image/jpeg" } })
      )
    );

    renderWithProviders(
      <ResultDialog task={videoSucceededTask} open onOpenChange={() => {}} />
    );

    await waitFor(() => expect(document.querySelector("video")).toBeInTheDocument());
    expect(screen.getByText(/15 秒/)).toBeInTheDocument();
  });

  it("视频加载失败显示可重试提示", async () => {
    server.use(
      http.get(`${API_BASE}/api/tasks/:id/result`, () =>
        HttpResponse.json(
          { type: "about:blank", title: "服务不可用", status: 503, code: "SERVICE_UNAVAILABLE" },
          { status: 503, headers: { "Content-Type": "application/problem+json" } }
        )
      ),
      http.get(
        `${API_BASE}/api/tasks/:id/result/poster`,
        () => new HttpResponse("jpeg", { headers: { "Content-Type": "image/jpeg" } })
      )
    );

    renderWithProviders(
      <ResultDialog task={videoSucceededTask} open onOpenChange={() => {}} />
    );

    expect(await screen.findByText("视频暂时无法播放")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新加载视频" })).toBeInTheDocument();
  });
});
