import type { TaskPage, TaskStatus } from "./types";

export function taskPollInterval(status: TaskStatus | null | undefined): number | false {
  switch (status) {
    case "RUNNING":
      return 1500;
    case "QUEUED":
    case "RETRYING":
      return 2500;
    default:
      return false;
  }
}

export function taskListPollInterval(page: TaskPage | undefined): number | false {
  if (!page || page.items.length === 0) {
    return false;
  }
  let hasActive = false;
  for (const task of page.items) {
    if (task.status === "RUNNING") {
      return 1500;
    }
    if (task.status === "QUEUED" || task.status === "RETRYING") {
      hasActive = true;
    }
  }
  return hasActive ? 2500 : false;
}

export function isActiveStatus(status: TaskStatus): boolean {
  return status === "QUEUED" || status === "RUNNING" || status === "RETRYING";
}
