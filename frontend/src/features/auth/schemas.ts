import { z } from "zod";

export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1)
    .regex(/^[^\s@]+@[^\s@]+\.[^\s@]+$/),
  password: z.string().min(1)
});

export type LoginInput = z.infer<typeof loginSchema>;

export type LoginFieldErrors = Partial<Record<keyof LoginInput, string>>;

export function validateLogin(input: LoginInput): LoginFieldErrors {
  const parsed = loginSchema.safeParse(input);
  if (parsed.success) {
    return {};
  }
  const errors: LoginFieldErrors = {};
  for (const issue of parsed.error.issues) {
    const field = issue.path[0];
    if (field === "email" || field === "password") {
      errors[field] ??= issue.message;
    }
  }
  return errors;
}
