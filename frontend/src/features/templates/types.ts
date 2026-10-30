export type Canvas = {
  width: number;
  height: number;
};

export type TemplateCategory = {
  id: string;
  name: string;
  sortOrder?: number;
};

export type TemplateSummary = {
  id: string;
  title: string;
  category: TemplateCategory;
  coverUrl: string;
  description: string;
  requiredImageCount: number;
  canvas: Canvas;
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
