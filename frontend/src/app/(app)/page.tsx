import { TemplatesView } from "@/features/templates/components/templates-view";

export default async function HomePage({
  searchParams
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const raw = Array.isArray(params.category) ? params.category[0] : params.category;
  return <TemplatesView initialCategory={raw || "all"} />;
}
