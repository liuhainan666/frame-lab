export type AssetView = {
  id: string;
  mimeType: string;
  sizeBytes: number;
  width: number;
  height: number;
  createdAt: string;
  contentUrl: string;
};

export type SlotError = "type" | "size" | "upload" | null;

export type SlotStatus = "empty" | "uploading" | "uploaded" | "error";

export type UploadSlotState = {
  index: number;
  status: SlotStatus;
  file: File | null;
  previewUrl: string | null;
  asset: AssetView | null;
  progress: number;
  error: SlotError;
  errorDetail: string | null;
};
