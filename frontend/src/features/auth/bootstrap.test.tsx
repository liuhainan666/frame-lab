import { HttpResponse, http } from "msw";
import { waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AuthBootstrap } from "@/features/auth/components/auth-bootstrap";
import { useAuthStore } from "@/features/auth/store";
import { API_BASE, sessionFixture } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";
import { renderWithProviders } from "@/test/utils";

describe("会话启动", () => {
  it("refresh 成功时恢复登录态", async () => {
    server.use(
      http.post(`${API_BASE}/api/auth/refresh`, () => HttpResponse.json(sessionFixture))
    );

    renderWithProviders(<AuthBootstrap />);

    await waitFor(() => expect(useAuthStore.getState().status).toBe("authenticated"));
    expect(useAuthStore.getState().accessToken).toBe("fresh-access-token");
  });

  it("refresh 失败时进入匿名态且不跳转", async () => {
    renderWithProviders(<AuthBootstrap />);

    await waitFor(() => expect(useAuthStore.getState().status).toBe("anonymous"));
    expect(useAuthStore.getState().user).toBeNull();
  });
});
