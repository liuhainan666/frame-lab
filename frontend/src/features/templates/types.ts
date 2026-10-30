export type Canvas = {
  width: number;
  height: number;
};

export type TemplateKind = "IMAGE" | "VIDEO";

export type TemplateOutput = {
  kind: TemplateKind;
  durationMs: number | null;
  width: number;
  height: number;
  hasAudio: boolean;
};

export type TemplateCategory = {
  id: string;
  name: string;
  sortOrder?: number;
};

export type TemplateSummary = {
  id: string;
  title: string;
  kind: TemplateKind;
  category: TemplateCategory;
  coverUrl: string;
  description: string;
  requiredImageCount: number;
  canvas: Canvas;
  effectTags: string[];
  output: TemplateOutput;
};

export type TemplateDetail = TemplateSummary & {
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
};

export type TemplateListResponse = {
  items: TemplateSummary[];
  total: number;
};

export type CategoryListResponse = {
  items: TemplateCategory[];
};
