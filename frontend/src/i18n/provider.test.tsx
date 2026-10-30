import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { LocaleSwitcher } from "@/features/account/components/locale-switcher";
import { DEFAULT_LOCALE, normalizeLocale, LOCALE_COOKIE } from "@/i18n/config";
import { readLocaleCookie, writeLocaleCookie } from "@/i18n/locale-cookie";
import { createTranslator } from "@/i18n/translate";
import { zhCN } from "@/i18n/messages/zh-CN";
import { renderWithProviders } from "@/test/utils";

describe("i18n 底座", () => {
  it("默认中文，切换 English 后写 Cookie、更新 html lang 并清空查询缓存", async () => {
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(<LocaleSwitcher />);
    queryClient.setQueryData(["templates", "zh-CN", "all"], [{ id: "travel-postcard" }]);

    expect(screen.getByRole("button", { name: "中文" })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "English" }));

    expect(screen.getByRole("button", { name: "English" })).toHaveAttribute(
      "aria-pressed",
      "true"
    );
    await waitFor(() => expect(readLocaleCookie()).toBe("en-US"));
    expect(document.documentElement.lang).toBe("en-US");
    expect(document.cookie).toContain(`${LOCALE_COOKIE}=en-US`);
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
  });

  it("读取持久化语言并归一化不支持的语言", () => {
    writeLocaleCookie("en-US");
    expect(readLocaleCookie()).toBe("en-US");
    expect(normalizeLocale("en")).toBe("en-US");
    expect(normalizeLocale("en-GB")).toBe("en-US");
    expect(normalizeLocale("zh-TW")).toBe("zh-CN");
    expect(normalizeLocale("fr-FR")).toBe(DEFAULT_LOCALE);
  });

  it("词典缺键时回退中文，不渲染 key", () => {
    const translate = createTranslator({
      "zh-CN": zhCN as unknown as Record<string, unknown>,
      "en-US": { auth: { title: "Welcome back" } }
    });

    expect(translate("en-US", "auth.description")).toBe(zhCN.auth.description);
    expect(translate("en-US", "auth.description")).not.toBe("auth.description");
  });
});
