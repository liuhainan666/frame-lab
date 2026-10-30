import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
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

function ActionToastTrigger() {
  const { toast } = useToast();
  const [retried, setRetried] = useState(0);
  return (
    <>
      <Button
        onClick={() =>
          toast({
            title: "任务状态暂时无法更新",
            tone: "warning",
            duration: null,
            action: { label: "重试", onClick: () => setRetried((count) => count + 1) }
          })
        }
      >
        触发常驻
      </Button>
      <span>{retried}</span>
    </>
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

  it("Toast 出现在顶部并可关闭", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ToastTrigger />);

    await user.click(screen.getByRole("button", { name: "触发" }));
    const toast = screen.getByTestId("toast");
    expect(toast).toHaveTextContent("任务已创建");
    expect(toast).toHaveTextContent("正在排队");
    expect(toast.parentElement?.parentElement).toHaveClass("top-[76px]");

    await user.click(screen.getByRole("button", { name: "关闭" }));
    expect(screen.queryByText("任务已创建")).not.toBeInTheDocument();
  });

  it("常驻 Toast 可带重试操作", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ActionToastTrigger />);

    await user.click(screen.getByRole("button", { name: "触发常驻" }));
    await user.click(screen.getByRole("button", { name: "重试" }));

    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("任务状态暂时无法更新")).toBeInTheDocument();
  });
});
