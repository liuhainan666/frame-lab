import { StudioView } from "@/features/studio/components/studio-view";

export default async function TemplateStudioPage({
  params,
  searchParams
}: {
  params: Promise<{ templateId: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { templateId } = await params;
  const query = await searchParams;
  const raw = Array.isArray(query.fromCategory) ? query.fromCategory[0] : query.fromCategory;
  const backHref = raw ? `/?category=${encodeURIComponent(raw)}` : "/";
  return <StudioView templateId={templateId} backHref={backHref} />;
}
