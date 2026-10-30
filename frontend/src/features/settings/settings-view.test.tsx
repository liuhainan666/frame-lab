import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { SettingsView } from "@/features/settings/components/settings-view";
import { renderWithProviders } from "@/test/utils";

describe("个人设置", () => {
  it("展示四项设置并可查看隐私说明", async () => {
    const user = userEvent.setup();
    renderWithProviders(<SettingsView />);

    for (const name of ["账单管理", "服务条款", "隐私政策", "订阅政策"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }

    await user.click(screen.getByRole("button", { name: "隐私政策" }));

    expect(await screen.findByRole("heading", { name: "隐私政策" })).toBeInTheDocument();
    expect(screen.getByText("照片不会用于模型训练，也不会共享给第三方。")).toBeInTheDocument();
  });

  it("英文界面的标签与参考图一致", async () => {
    const user = userEvent.setup();
    renderWithProviders(<SettingsView />, { locale: "en-US" });

    for (const name of ["Manage Billing", "Terms of Service", "Privacy Policy", "Subscription Policy"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }

    await user.click(screen.getByRole("button", { name: "Subscription Policy" }));

    expect(await screen.findByText("No automatic charges or hidden fees.")).toBeInTheDocument();
  });
});
