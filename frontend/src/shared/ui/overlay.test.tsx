import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { useToast } from "@/shared/ui/toast";
import { Button } from "@/shared/ui/button";
import { Dialog, DialogContent, DialogTrigger } from "@/shared/ui/dialog";
import { renderWithProviders } from "@/test/utils";

function ToastTrigger() {
  const { toast } = useToast();
  return (
    <Button
      onClick={() =>
        toast({ title: "任务已创建", description: "正在排队", tone: "success" })
      }
    >
      触发
    </Button>
  );
}

describe("Dialog 与 Toast", () => {
  it("Dialog 打开后可用 Esc 关闭", async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <Dialog>
        <DialogTrigger asChild>
          <Button>打开</Button>
        </DialogTrigger>
        <DialogContent title="账号" description="演示账号">
          内容
        </DialogContent>
      </Dialog>
    );

    await user.click(screen.getByRole("button", { name: "打开" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("内容")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("Toast 出现后可关闭", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ToastTrigger />);

    await user.click(screen.getByRole("button", { name: "触发" }));
    expect(screen.getByText("任务已创建")).toBeInTheDocument();
    expect(screen.getByText("正在排队")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "关闭" }));
    expect(screen.queryByText("任务已创建")).not.toBeInTheDocument();
  });
});
