export type TaskStatus = "QUEUED" | "RUNNING" | "RETRYING" | "SUCCEEDED" | "FAILED";
export type TaskStage = "MATERIAL_CHECK" | "GENERATE" | "FINALIZE";
export type StepStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";
export type AttemptStatus = "RUNNING" | "SUCCEEDED" | "FAILED" | "INTERRUPTED";

export type TaskError = {
  code: string;
  message: string;
  simulated: boolean;
};

export type TaskFailure = TaskError & {
  stage: TaskStage;
};

export type TaskRetry = {
  retryCount: number;
  attemptCount: number;
  maxAttempts: number;
  nextRetryAt: string | null;
  lastError: TaskError | null;
};

export type TaskResult = {
  available: boolean;
  url: string | null;
  width: number | null;
  height: number | null;
  sizeBytes?: number | null;
};

export type TaskSummary = {
  id: string;
  templateId: string;
  templateTitle: string;
  templateCoverUrl: string;
  status: TaskStatus;
  currentStage: TaskStage | null;
  retry: TaskRetry;
  resumeCount: number;
  failure: TaskFailure | null;
  result: TaskResult | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
};

export type TaskAttempt = {
  attemptNo: number;
  status: AttemptStatus;
  errorCode: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};

export type TaskStep = {
  seq: number;
  stage: TaskStage;
  status: StepStatus;
  attemptCount: number;
  message: string | null;
  errorCode: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  attempts: TaskAttempt[];
};

export type TaskMaterial = {
  slotIndex: number;
  assetId: string;
  contentUrl: string;
};

export type TaskDetail = TaskSummary & {
  canvas: { width: number; height: number };
  steps: TaskStep[];
  materials: TaskMaterial[];
};

export type TaskPage = {
  items: TaskSummary[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
};

export type TaskListFilters = {
  status?: string;
  templateId?: string;
  page?: number;
  size?: number;
};

export type TaskSubmitRequest = {
  templateId: string;
  assetIds: string[];
  clientRequestId: string;
};
