import type { TemplateDetail, TemplateSummary } from "@/features/templates/types";

export const travelCategory = { id: "travel", name: "旅行风景" };
export const minimalCategory = { id: "minimal", name: "简约留白" };

export const travelTemplate: TemplateSummary = {
  id: "travel-postcard",
  title: "旅行明信片",
  kind: "IMAGE",
  category: travelCategory,
  coverUrl: "/assets/templates/travel-postcard.webp",
  description: "把两张旅途照片拼成一张可以寄出的明信片。",
  requiredImageCount: 2,
  canvas: { width: 1600, height: 1200 },
  effectTags: ["travel", "warm"],
  output: { kind: "IMAGE", durationMs: null, width: 1600, height: 1200, hasAudio: false }
};

export const minimalTemplate: TemplateSummary = {
  id: "minimal-frame",
  title: "留白相框",
  kind: "IMAGE",
  category: minimalCategory,
  coverUrl: "/assets/templates/minimal-frame.webp",
  description: "给一张照片留出呼吸感。",
  requiredImageCount: 1,
  canvas: { width: 1200, height: 1600 },
  effectTags: ["portrait", "clean"],
  output: { kind: "IMAGE", durationMs: null, width: 1200, height: 1600, hasAudio: false }
};

export const videoTemplate: TemplateSummary = {
  id: "city-walk-15s",
  title: "城市漫游",
  kind: "VIDEO",
  category: travelCategory,
  coverUrl: "/assets/templates/golden-hour.webp",
  description: "四张照片，十五秒的城市慢镜头。",
  requiredImageCount: 5,
  canvas: { width: 1080, height: 1920 },
  effectTags: ["warm", "cinematic"],
  output: { kind: "VIDEO", durationMs: 15000, width: 1080, height: 1920, hasAudio: true }
};

export const travelTemplateDetail: TemplateDetail = {
  ...travelTemplate,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 5 * 1024 * 1024
};

export const videoTemplateDetail: TemplateDetail = {
  ...videoTemplate,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 10 * 1024 * 1024
};
