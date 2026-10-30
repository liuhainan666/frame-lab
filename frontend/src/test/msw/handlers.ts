import { http, HttpResponse } from "msw";

export const API_BASE = "http://localhost:8080";

export const demoUser = {
  id: 1,
  email: "demo@framelab.local",
  displayName: "林小满",
  role: "USER" as const,
  createdAt: "2026-10-01T02:00:00.000Z"
};

export const sessionFixture = {
  accessToken: "fresh-access-token",
  tokenType: "Bearer" as const,
  expiresIn: 900,
  user: demoUser
};

export const handlers = [
  http.post(`${API_BASE}/api/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string };
    if (body.email === "bad@framelab.local" || body.password !== "Demo1234!") {
      return HttpResponse.json(
        {
          type: "https://framelab.local/problems/invalid-credentials",
          title: "邮箱或密码不正确",
          status: 401,
          detail: "邮箱或密码不正确",
          code: "INVALID_CREDENTIALS"
        },
        { status: 401, headers: { "Content-Type": "application/problem+json" } }
      );
    }
    return HttpResponse.json(sessionFixture);
  }),
  http.post(`${API_BASE}/api/auth/refresh`, () => {
    return HttpResponse.json(
      {
        type: "https://framelab.local/problems/refresh-token-invalid",
        title: "登录已过期",
        status: 401,
        code: "REFRESH_TOKEN_INVALID"
      },
      { status: 401, headers: { "Content-Type": "application/problem+json" } }
    );
  }),
  http.post(`${API_BASE}/api/auth/logout`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${API_BASE}/api/auth/logout-all`, () => new HttpResponse(null, { status: 204 }))
];
