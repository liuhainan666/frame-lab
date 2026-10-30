import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { AccountMenu } from "@/features/account/components/account-menu";
import { useAuthStore } from "@/features/auth/store";
import { sessionFixture } from "@/test/msw/handlers";
import { renderWithProviders } from "@/test/utils";

describe("账号弹层", () => {
  afterEach(() => {
    useAuthStore.getState().clearSession();
  });

  it("提供退出登录与退出所有设备两个入口", async () => {
    const user = userEvent.setup();
    useAuthStore.getState().setSession(sessionFixture);
    renderWithProviders(<AccountMenu />);

    await user.click(screen.getByRole("button", { name: "打开账号菜单" }));

    expect(await screen.findByRole("button", { name: "退出登录" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "退出所有设备" })).toBeInTheDocument();
  });

  it("在账号弹层内展示四项设置并可切换说明", async () => {
    const user = userEvent.setup();
    useAuthStore.getState().setSession(sessionFixture);
    renderWithProviders(<AccountMenu />);

    await user.click(screen.getByRole("button", { name: "打开账号菜单" }));

    for (const name of ["账单管理", "服务条款", "隐私政策", "订阅政策"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }

    await user.click(screen.getByRole("button", { name: "隐私政策" }));

    expect(await screen.findByRole("heading", { name: "隐私政策" })).toBeInTheDocument();
    expect(screen.getByText("照片不会用于模型训练，也不会共享给第三方。")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "返回账号" }));

    expect(await screen.findByRole("heading", { name: "账号" })).toBeInTheDocument();
  });

  it("英文界面显示对应文案", async () => {
    const user = userEvent.setup();
    useAuthStore.getState().setSession(sessionFixture);
    renderWithProviders(<AccountMenu />, { locale: "en-US" });

    await user.click(screen.getByRole("button", { name: "Open account menu" }));

    expect(await screen.findByRole("button", { name: "Sign out of all devices" })).toBeInTheDocument();
    for (const name of ["Manage Billing", "Terms of Service", "Privacy Policy", "Subscription Policy"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }
  });
});
