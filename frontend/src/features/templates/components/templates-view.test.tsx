import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { TemplatesView } from "@/features/templates/components/templates-view";
import { API_BASE } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";
import {
  minimalCategory,
  minimalTemplate,
  travelCategory,
  travelTemplate
} from "@/test/fixtures/templates";
import { renderWithProviders } from "@/test/utils";

const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/"
}));

function mockTemplates() {
  const requested: Array<string | null> = [];
  server.use(
    http.get(`${API_BASE}/api/template-categories`, () =>
      HttpResponse.json({ items: [travelCategory, minimalCategory] })
    ),
    http.get(`${API_BASE}/api/templates`, ({ request }) => {
      const category = new URL(request.url).searchParams.get("category");
      requested.push(category);
      const items = category === "minimal" ? [minimalTemplate] : [travelTemplate, minimalTemplate];
      return HttpResponse.json({ items, total: items.length });
    })
  );
  return requested;
}

describe("模板列表页", () => {
  it("渲染分类、卡片与素材数量，切换分类后重新查询", async () => {
    const user = userEvent.setup();
    const requested = mockTemplates();
    renderWithProviders(<TemplatesView initialCategory="all" />);

    expect(await screen.findAllByTestId("template-card")).toHaveLength(2);
    expect(screen.getByText("旅行明信片")).toBeInTheDocument();
    expect(screen.getByText("留白相框")).toBeInTheDocument();
    expect(screen.getByText(/2 张素材/)).toBeInTheDocument();
    expect(screen.getByText(/1 张素材/)).toBeInTheDocument();
    expect(screen.getByText("2 个模板")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "简约留白" }));

    await waitFor(() => expect(screen.getAllByTestId("template-card")).toHaveLength(1));
    expect(screen.getByText("留白相框")).toBeInTheDocument();
    expect(requested).toContain("minimal");
    expect(router.replace).toHaveBeenCalledWith("/?category=minimal", { scroll: false });
  });

  it("空分类显示空态并可查看全部", async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API_BASE}/api/template-categories`, () =>
        HttpResponse.json({ items: [travelCategory] })
      ),
      http.get(`${API_BASE}/api/templates`, () =>
        HttpResponse.json({ items: [], total: 0 })
      )
    );

    renderWithProviders(<TemplatesView initialCategory="travel" />);

    expect(await screen.findByText("这个分类还没有模板")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "查看全部" }));
    expect(router.replace).toHaveBeenCalledWith("/", { scroll: false });
  });

  it("加载失败显示错误态，重试后恢复", async () => {
    const user = userEvent.setup();
    let fail = true;
    server.use(
      http.get(`${API_BASE}/api/template-categories`, () =>
        HttpResponse.json({ items: [travelCategory] })
      ),
      http.get(`${API_BASE}/api/templates`, () => {
        if (fail) {
          return HttpResponse.json(
            {
              type: "about:blank",
              title: "服务暂时不可用",
              status: 503,
              code: "SERVICE_UNAVAILABLE"
            },
            { status: 503, headers: { "Content-Type": "application/problem+json" } }
          );
        }
        return HttpResponse.json({ items: [travelTemplate], total: 1 });
      })
    );

    renderWithProviders(<TemplatesView initialCategory="all" />);
    expect(await screen.findByText("模板暂时加载失败")).toBeInTheDocument();

    fail = false;
    await user.click(screen.getByRole("button", { name: "重新加载" }));
    expect(await screen.findByText("旅行明信片")).toBeInTheDocument();
  });

  it("英文界面使用英文文案", async () => {
    mockTemplates();
    renderWithProviders(<TemplatesView initialCategory="all" />, { locale: "en-US" });

    expect(await screen.findByRole("heading", { name: "Choose a template" })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "All" })).toBeInTheDocument();
    expect(await screen.findByText("2 templates")).toBeInTheDocument();
  });
});
