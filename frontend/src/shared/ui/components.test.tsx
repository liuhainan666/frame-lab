import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { renderWithProviders } from "@/test/utils";

describe("基础组件", () => {
  it("Button 可键盘点击，loading 时禁用", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    const { rerender } = renderWithProviders(<Button onClick={onClick}>开始制作</Button>);

    const button = screen.getByRole("button", { name: "开始制作" });
    button.focus();
    await user.keyboard("{Enter}");
    expect(onClick).toHaveBeenCalledTimes(1);

    rerender(<Button loading>开始制作</Button>);
    expect(screen.getByRole("button")).toBeDisabled();
    expect(screen.getByRole("button")).toHaveAttribute("aria-busy", "true");
  });

  it("Input 关联标签与错误描述", () => {
    renderWithProviders(
      <Input label="邮箱" value="" onChange={() => {}} error="请输入有效的邮箱地址" />
    );

    const input = screen.getByLabelText("邮箱");
    expect(input).toHaveAttribute("aria-invalid", "true");
    const error = screen.getByText("请输入有效的邮箱地址");
    expect(input).toHaveAttribute("aria-describedby", error.id);
  });
});
