import { LoginView } from "@/features/auth/components/login-view";
import { sanitizeReturnTo } from "@/shared/lib/return-to";

export default async function LoginPage({
  searchParams
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const raw = Array.isArray(params.returnTo) ? params.returnTo[0] : params.returnTo;
  return <LoginView returnTo={sanitizeReturnTo(raw)} />;
}
