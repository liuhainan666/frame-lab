export function sanitizeReturnTo(value?: string | null): string {
  if (!value || !value.startsWith("/") || value.startsWith("//")) {
    return "/";
  }
  if (value === "/login" || value.startsWith("/login?")) {
    return "/";
  }
  return value;
}
