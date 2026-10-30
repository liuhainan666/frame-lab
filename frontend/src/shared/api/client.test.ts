import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { configureAuthBridge } from "@/shared/api/auth-bridge";
import { apiFetch } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import type { SessionPayload } from "@/shared/api/types";
import { writeLocaleCookie } from "@/i18n/locale-cookie";
import { API_BASE, sessionFixture } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";

function problem(status: number, code: string) {
  return HttpResponse.json(
    { title: code, status, code },
    { status, headers: { "Content-Type": "application/problem+json" } }
  );
}

describe("API 客户端", () => {
  it("401 TOKEN_EXPIRED 时单飞刷新并各重放一次", async () => {
    let token = "expired-token";
    let refreshCalls = 0;
    let protectedCalls = 0;
    const refreshed = vi.fn();
    configureAuthBridge({
      getAccessToken: () => token,
      onSessionRefreshed: (session: SessionPayload) => {
        token = session.accessToken;
        refreshed(session);
      },
      onSessionExpired: () => {}
    });

    server.use(
      http.get(`${API_BASE}/api/tasks/task-1`, ({ request }) => {
        protectedCalls += 1;
        if (request.headers.get("Authorization") === "Bearer fresh-access-token") {
          return HttpResponse.json({ id: "task-1", status: "QUEUED" });
        }
        return problem(401, "TOKEN_EXPIRED");
      }),
      http.post(`${API_BASE}/api/auth/refresh`, () => {
        refreshCalls += 1;
        return HttpResponse.json(sessionFixture);
      })
    );

    const [first, second] = await Promise.all([
      apiFetch<{ id: string }>("/api/tasks/task-1"),
      apiFetch<{ id: string }>("/api/tasks/task-1")
    ]);

    expect(first.id).toBe("task-1");
    expect(second.id).toBe("task-1");
    expect(refreshCalls).toBe(1);
    expect(refreshed).toHaveBeenCalledTimes(1);
    expect(protectedCalls).toBe(4);
  });

  it("刷新失败时清理会话并抛出原 401", async () => {
    const expired = vi.fn();
    configureAuthBridge({
      getAccessToken: () => "expired-token",
      onSessionRefreshed: () => {},
      onSessionExpired: expired
    });
    server.use(http.get(`${API_BASE}/api/tasks/task-2`, () => problem(401, "TOKEN_EXPIRED")));

    await expect(apiFetch("/api/tasks/task-2")).rejects.toMatchObject({
      status: 401,
      code: "TOKEN_EXPIRED"
    });
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it("按 Cookie 语言发送 Accept-Language", async () => {
    writeLocaleCookie("en-US");
    let received: string | null = null;
    server.use(
      http.get(`${API_BASE}/api/templates`, ({ request }) => {
        received = request.headers.get("Accept-Language");
        return HttpResponse.json({ items: [] });
      })
    );

    await apiFetch("/api/templates");
    expect(received).toBe("en-US");
  });

  it("problem+json 被映射为带 code 与字段错误的 ApiError", async () => {
    configureAuthBridge({ getAccessToken: () => null });
    server.use(
      http.post(`${API_BASE}/api/auth/login`, () =>
        HttpResponse.json(
          {
            title: "请求参数不合法",
            status: 400,
            code: "VALIDATION_FAILED",
            detail: "邮箱格式不正确",
            errors: [{ field: "email", message: "邮箱格式不正确" }]
          },
          { status: 400, headers: { "Content-Type": "application/problem+json" } }
        )
      )
    );

    try {
      await apiFetch("/api/auth/login", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify({ email: "x", password: "y" })
      });
      throw new Error("expected failure");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).code).toBe("VALIDATION_FAILED");
      expect((error as ApiError).fieldError("email")).toBe("邮箱格式不正确");
    }
  });
});
