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
    expect(screen.getByRole("link", { name: "个人设置" })).toHaveAttribute("href", "/settings");
  });

  it("英文界面显示对应文案", async () => {
    const user = userEvent.setup();
    useAuthStore.getState().setSession(sessionFixture);
    renderWithProviders(<AccountMenu />, { locale: "en-US" });

    await user.click(screen.getByRole("button", { name: "Open account menu" }));

    expect(await screen.findByRole("button", { name: "Sign out of all devices" })).toBeInTheDocument();
  });
});
