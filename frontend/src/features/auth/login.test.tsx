import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { LoginView } from "@/features/auth/components/login-view";
import { useAuthStore } from "@/features/auth/store";
import { sanitizeReturnTo } from "@/shared/lib/return-to";
import { API_BASE } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";
import { renderWithProviders } from "@/test/utils";

const router = vi.hoisted(() => ({
  replace: vi.fn(),
  push: vi.fn(),
  refresh: vi.fn()
}));

vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/login"
}));

describe("登录页", () => {
  it("填入演示账号并登录后回到 returnTo", async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginView returnTo="/history" />);

    await user.click(screen.getByText("使用演示账号"));
    await user.click(screen.getByRole("button", { name: "填入 demo@framelab.local" }));

    expect(screen.getByLabelText("邮箱")).toHaveValue("demo@framelab.local");
    expect(screen.getByLabelText("密码")).toHaveValue("Demo1234!");

    await user.click(screen.getByRole("button", { name: "登录" }));

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/history"));
    expect(useAuthStore.getState().status).toBe("authenticated");
    expect(useAuthStore.getState().user?.displayName).toBe("林小满");
  });

  it("本地校验邮箱与密码", async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginView returnTo="/" />);

    await user.click(screen.getByRole("button", { name: "登录" }));
    expect(await screen.findByText("请输入有效的邮箱地址")).toBeInTheDocument();
    expect(screen.getByText("请输入密码")).toBeInTheDocument();
  });

  it("显示 API 本地化的登录错误", async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginView returnTo="/" />);

    await user.type(screen.getByLabelText("邮箱"), "bad@framelab.local");
    await user.type(screen.getByLabelText("密码"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("邮箱或密码不正确");
    expect(useAuthStore.getState().status).toBe("unknown");
  });

  it("网络失败时用顶部气泡提示", async () => {
    server.use(http.post(`${API_BASE}/api/auth/login`, () => HttpResponse.error()));
    const user = userEvent.setup();
    renderWithProviders(<LoginView returnTo="/" />);

    await user.type(screen.getByLabelText("邮箱"), "demo@framelab.local");
    await user.type(screen.getByLabelText("密码"), "Demo1234!");
    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "网络连接失败，请检查后重试"
    );
    expect(useAuthStore.getState().status).toBe("unknown");
  });

  it("把英文界面下的登录错误切换到英文", async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginView returnTo="/" />, { locale: "en-US" });

    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Enter a valid email address")).toBeInTheDocument();
    expect(screen.getByText("Enter your password")).toBeInTheDocument();
  });

  it("拒绝站外 returnTo", () => {
    expect(sanitizeReturnTo("https://evil.example")).toBe("/");
    expect(sanitizeReturnTo("//evil.example")).toBe("/");
    expect(sanitizeReturnTo("/login?returnTo=/x")).toBe("/");
    expect(sanitizeReturnTo("/history?status=active")).toBe("/history?status=active");
  });
});
