import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/features/auth/store";
import { StudioView } from "@/features/studio/components/studio-view";
import { API_BASE, sessionFixture } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";
import { travelTemplateDetail, videoTemplateDetail } from "@/test/fixtures/templates";
import { queuedTaskDetail, taskPage } from "@/test/fixtures/tasks";
import { renderWithProviders } from "@/test/utils";

import type { AssetView } from "../types";

const uploadMock = vi.hoisted(() => vi.fn());
const deleteMock = vi.hoisted(() => vi.fn());
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));

vi.mock("../api", () => ({
  uploadAsset: uploadMock,
  deleteAsset: deleteMock
}));

vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/templates/travel-postcard"
}));

function asset(id: string): AssetView {
  return {
    id,
    mimeType: "image/jpeg",
    sizeBytes: 1,
    width: 100,
    height: 100,
    createdAt: "2026-10-28T08:21:00.000Z",
    contentUrl: `/api/assets/${id}/content`
  };
}

function imageFile(name: string, bytes = 4): File {
  return new File([new Uint8Array(bytes)], name, { type: "image/jpeg" });
}

function mockStudio(overrides?: { submit?: () => Response }) {
  const submitted: Array<Record<string, unknown>> = [];
  server.use(
    http.get(`${API_BASE}/api/templates/travel-postcard`, () =>
      HttpResponse.json(travelTemplateDetail)
    ),
    http.get(`${API_BASE}/api/tasks`, () => HttpResponse.json(taskPage([]))),
    http.post(`${API_BASE}/api/tasks`, async ({ request }) => {
      submitted.push((await request.json()) as Record<string, unknown>);
      if (overrides?.submit) {
        return overrides.submit();
      }
      return HttpResponse.json(queuedTaskDetail, { status: 202 });
    })
  );
  return submitted;
}

beforeEach(() => {
  useAuthStore.getState().setSession(sessionFixture);
  uploadMock.mockReset();
  deleteMock.mockReset();
  deleteMock.mockResolvedValue(undefined);
  router.push.mockReset();
});

describe("制作台", () => {
  it("上传两张素材后提交一次并清空草稿", async () => {
    const user = userEvent.setup();
    const submitted = mockStudio();
    let seq = 0;
    uploadMock.mockImplementation(async () => asset(`asset-${++seq}`));

    renderWithProviders(<StudioView templateId="travel-postcard" />);

    const first = await screen.findByLabelText("选择第 1 张图片");
    const second = screen.getByLabelText("选择第 2 张图片");
    await user.upload(first, imageFile("one.jpg"));
    await user.upload(second, imageFile("two.jpg"));

    expect(await screen.findByText("2 / 2")).toBeInTheDocument();
    expect(uploadMock).toHaveBeenCalledTimes(2);

    const submit = screen.getAllByRole("button", { name: "开始制作" })[0];
    expect(submit).toBeEnabled();
    await user.click(submit);
    await user.click(submit);

    expect(await screen.findByText("任务已创建，正在排队")).toBeInTheDocument();
    expect(await screen.findByText("0 / 2")).toBeInTheDocument();
    expect(submitted).toHaveLength(1);
    expect(submitted[0]?.templateId).toBe("travel-postcard");
    expect(submitted[0]?.assetIds).toEqual(["asset-1", "asset-2"]);
    expect(submitted[0]?.clientRequestId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
    );
  });

  it("本地拒绝不支持的格式与超限大小", async () => {
    const user = userEvent.setup({ applyAccept: false });
    mockStudio();

    renderWithProviders(<StudioView templateId="travel-postcard" />);

    const first = await screen.findByLabelText("选择第 1 张图片");
    await user.upload(first, new File(["hello"], "notes.txt", { type: "text/plain" }));
    expect(await screen.findByText(/格式不支持/)).toBeInTheDocument();
    expect(uploadMock).not.toHaveBeenCalled();

    const second = screen.getByLabelText("选择第 2 张图片");
    await user.upload(second, imageFile("huge.jpg", 6 * 1024 * 1024));
    expect(await screen.findByText(/超过 5 MB/)).toBeInTheDocument();
    expect(uploadMock).not.toHaveBeenCalled();
  });

  it("移除与替换已上传素材会调用 DELETE", async () => {
    const user = userEvent.setup();
    mockStudio();
    let seq = 0;
    uploadMock.mockImplementation(async () => asset(`asset-${++seq}`));

    renderWithProviders(<StudioView templateId="travel-postcard" />);

    const first = await screen.findByLabelText("选择第 1 张图片");
    await user.upload(first, imageFile("one.jpg"));
    expect(await screen.findByText("1 / 2")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "移除第 1 张图片" }));
    expect(deleteMock).toHaveBeenCalledWith("asset-1");
    expect(await screen.findByText("0 / 2")).toBeInTheDocument();

    await user.upload(first, imageFile("again.jpg"));
    expect(await screen.findByText("1 / 2")).toBeInTheDocument();
    await user.upload(first, imageFile("replacement.jpg"));
    await waitFor(() => expect(uploadMock).toHaveBeenCalledTimes(3));
    expect(deleteMock).toHaveBeenCalledWith("asset-2");
  });

  it("提交失败后重试复用同一个 clientRequestId", async () => {
    const user = userEvent.setup();
    let fail = true;
    const submitted = mockStudio({
      submit: () =>
        fail
          ? HttpResponse.json(
              {
                type: "about:blank",
                title: "服务暂时不可用",
                status: 503,
                code: "SERVICE_UNAVAILABLE"
              },
              { status: 503, headers: { "Content-Type": "application/problem+json" } }
            )
          : HttpResponse.json(queuedTaskDetail, { status: 202 })
    });
    let seq = 0;
    uploadMock.mockImplementation(async () => asset(`asset-${++seq}`));

    renderWithProviders(<StudioView templateId="travel-postcard" />);

    await user.upload(await screen.findByLabelText("选择第 1 张图片"), imageFile("one.jpg"));
    await user.upload(screen.getByLabelText("选择第 2 张图片"), imageFile("two.jpg"));
    expect(await screen.findByText("2 / 2")).toBeInTheDocument();

    await user.click(screen.getAllByRole("button", { name: "开始制作" })[0]);
    const retry = await screen.findAllByRole("button", { name: "重试提交" });
    expect(submitted).toHaveLength(1);

    fail = false;
    await user.click(retry[0]);

    expect(await screen.findByText("任务已创建，正在排队")).toBeInTheDocument();
    expect(submitted).toHaveLength(2);
    expect(submitted[0]?.clientRequestId).toBe(submitted[1]?.clientRequestId);
  });

  it("匿名访问锁定上传并引导登录", async () => {
    useAuthStore.getState().clearSession();
    useAuthStore.setState({ status: "anonymous" });
    mockStudio();
    const user = userEvent.setup();

    renderWithProviders(<StudioView templateId="travel-postcard" />);

    expect(await screen.findAllByText("登录后上传")).toHaveLength(2);
    expect(screen.getByText("登录后查看制作记录")).toBeInTheDocument();

    await user.click(screen.getAllByRole("button", { name: "登录后开始制作" })[0]);
    expect(router.push).toHaveBeenCalledWith(
      `/login?returnTo=${encodeURIComponent("/templates/travel-postcard")}`
    );
  });

  it("视频模板展示输出摘要、效果标签与生成视频按钮", async () => {
    server.use(
      http.get(`${API_BASE}/api/templates/city-walk-15s`, () =>
        HttpResponse.json(videoTemplateDetail)
      ),
      http.get(`${API_BASE}/api/tasks`, () => HttpResponse.json(taskPage([])))
    );

    renderWithProviders(<StudioView templateId="city-walk-15s" />);

    expect(await screen.findByRole("heading", { name: "城市漫游" })).toBeInTheDocument();
    expect(screen.getByText("15 秒视频 · 1080×1920 · 含背景音乐")).toBeInTheDocument();
    expect(screen.getAllByLabelText(/选择第 \d 张图片/)).toHaveLength(5);
    expect(screen.getAllByRole("button", { name: "生成视频" }).length).toBeGreaterThan(0);
    expect(screen.getByText("电影感")).toBeInTheDocument();
  });
});
