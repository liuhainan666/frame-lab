import type { MessageKey } from "@/i18n/messages/zh-CN";
import type { TaskStatus, TaskSummary } from "./types";

export type DisplayStatus = TaskStatus | "RESUMING";

export function displayStatus(task: Pick<TaskSummary, "status" | "resumeCount">): DisplayStatus {
  if (task.status === "QUEUED" && task.resumeCount > 0) {
    return "RESUMING";
  }
  return task.status;
}

export function statusLabelKey(status: DisplayStatus): MessageKey {
  return `status.${status}` as MessageKey;
}

export function statusTone(
  status: DisplayStatus
): "neutral" | "accent" | "success" | "warning" | "danger" {
  switch (status) {
    case "RUNNING":
      return "accent";
    case "RETRYING":
    case "RESUMING":
      return "warning";
    case "SUCCEEDED":
      return "success";
    case "FAILED":
      return "danger";
    default:
      return "neutral";
  }
}

export function stageLabelKey(stage: string | null | undefined): MessageKey | null {
  if (!stage) {
    return null;
  }
  if (stage === "MATERIAL_CHECK" || stage === "GENERATE" || stage === "FINALIZE") {
    return `stage.${stage}` as MessageKey;
  }
  return null;
}
