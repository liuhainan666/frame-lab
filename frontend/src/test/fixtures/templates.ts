import type { TemplateDetail, TemplateSummary } from "@/features/templates/types";

export const travelCategory = { id: "travel", name: "旅行风景" };
export const minimalCategory = { id: "minimal", name: "简约留白" };

export const travelTemplate: TemplateSummary = {
  id: "travel-postcard",
  title: "旅行明信片",
  category: travelCategory,
  coverUrl: "/assets/templates/travel-postcard.webp",
  description: "把两张旅途照片拼成一张可以寄出的明信片。",
  requiredImageCount: 2,
  canvas: { width: 1600, height: 1200 }
};

export const minimalTemplate: TemplateSummary = {
  id: "minimal-frame",
  title: "留白相框",
  category: minimalCategory,
  coverUrl: "/assets/templates/minimal-frame.webp",
  description: "给一张照片留出呼吸感。",
  requiredImageCount: 1,
  canvas: { width: 1200, height: 1600 }
};

export const travelTemplateDetail: TemplateDetail = {
  ...travelTemplate,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 5 * 1024 * 1024
};
