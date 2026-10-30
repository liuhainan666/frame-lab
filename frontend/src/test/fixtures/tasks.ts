import type {
  TaskDetail,
  TaskPage,
  TaskSummary
} from "@/features/tasks/types";

const base: Omit<TaskSummary, "id" | "status"> = {
  templateId: "travel-postcard",
  templateTitle: "旅行明信片",
  templateCoverUrl: "/assets/templates/travel-postcard.webp",
  currentStage: null,
  retry: {
    retryCount: 0,
    attemptCount: 0,
    maxAttempts: 3,
    nextRetryAt: null,
    lastError: null
  },
  resumeCount: 0,
  failure: null,
  result: null,
  createdAt: "2026-10-28T08:21:00.000Z",
  updatedAt: "2026-10-28T08:21:05.200Z",
  finishedAt: null
};

export const queuedTask: TaskSummary = {
  ...base,
  id: "0f0c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e44",
  status: "QUEUED"
};

export const resumingTask: TaskSummary = {
  ...base,
  id: "1a1c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e55",
  status: "QUEUED",
  resumeCount: 1,
  currentStage: "GENERATE"
};

export const retryingTask: TaskSummary = {
  ...base,
  id: "2b2c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e66",
  status: "RETRYING",
  currentStage: "GENERATE",
  retry: {
    retryCount: 1,
    attemptCount: 1,
    maxAttempts: 3,
    nextRetryAt: "2099-10-28T08:21:30.000Z",
    lastError: {
      code: "GENERATION_FAILED",
      message: "模拟生成失败",
      simulated: true
    }
  }
};

export const failedTask: TaskSummary = {
  ...base,
  id: "3c3c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e77",
  status: "FAILED",
  currentStage: "GENERATE",
  retry: {
    retryCount: 3,
    attemptCount: 3,
    maxAttempts: 3,
    nextRetryAt: null,
    lastError: {
      code: "GENERATION_FAILED",
      message: "生成失败",
      simulated: false
    }
  },
  failure: {
    code: "GENERATION_FAILED",
    message: "生成失败，请稍后重试。",
    simulated: false,
    stage: "GENERATE"
  },
  finishedAt: "2026-10-28T08:23:05.000Z"
};

export const succeededTask: TaskSummary = {
  ...base,
  id: "4d4c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e88",
  status: "SUCCEEDED",
  currentStage: "FINALIZE",
  result: {
    available: true,
    url: "/api/tasks/4d4c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e88/result",
    width: 1600,
    height: 1200,
    sizeBytes: 2417780
  },
  finishedAt: "2026-10-28T08:21:05.160Z"
};

export const failedTaskDetail: TaskDetail = {
  ...failedTask,
  canvas: { width: 1600, height: 1200 },
  materials: [
    {
      slotIndex: 1,
      assetId: "5c9b2f7e-3a41-4f0e-9d5b-2a7c1e8f4b10",
      contentUrl: "/api/assets/5c9b2f7e-3a41-4f0e-9d5b-2a7c1e8f4b10/content"
    }
  ],
  steps: [
    {
      seq: 1,
      stage: "MATERIAL_CHECK",
      status: "SUCCEEDED",
      attemptCount: 1,
      message: "素材检查通过",
      errorCode: null,
      startedAt: "2026-10-28T08:21:00.120Z",
      finishedAt: "2026-10-28T08:21:00.920Z",
      attempts: [
        {
          attemptNo: 1,
          status: "SUCCEEDED",
          errorCode: null,
          errorMessage: null,
          startedAt: "2026-10-28T08:21:00.120Z",
          finishedAt: "2026-10-28T08:21:00.920Z"
        }
      ]
    },
    {
      seq: 2,
      stage: "GENERATE",
      status: "FAILED",
      attemptCount: 2,
      message: "生成失败",
      errorCode: "GENERATION_FAILED",
      startedAt: "2026-10-28T08:21:01.000Z",
      finishedAt: "2026-10-28T08:23:00.000Z",
      attempts: [
        {
          attemptNo: 1,
          status: "INTERRUPTED",
          errorCode: null,
          errorMessage: null,
          startedAt: "2026-10-28T08:21:01.000Z",
          finishedAt: "2026-10-28T08:21:10.000Z"
        },
        {
          attemptNo: 2,
          status: "FAILED",
          errorCode: "GENERATION_FAILED",
          errorMessage: "生成失败，请稍后重试。",
          startedAt: "2026-10-28T08:21:20.000Z",
          finishedAt: "2026-10-28T08:23:00.000Z"
        }
      ]
    },
    {
      seq: 3,
      stage: "FINALIZE",
      status: "SKIPPED",
      attemptCount: 0,
      message: null,
      errorCode: null,
      startedAt: null,
      finishedAt: null,
      attempts: []
    }
  ]
};

export const queuedTaskDetail: TaskDetail = {
  ...queuedTask,
  canvas: { width: 1600, height: 1200 },
  materials: [],
  steps: [
    {
      seq: 1,
      stage: "MATERIAL_CHECK",
      status: "PENDING",
      attemptCount: 0,
      message: null,
      errorCode: null,
      startedAt: null,
      finishedAt: null,
      attempts: []
    },
    {
      seq: 2,
      stage: "GENERATE",
      status: "PENDING",
      attemptCount: 0,
      message: null,
      errorCode: null,
      startedAt: null,
      finishedAt: null,
      attempts: []
    },
    {
      seq: 3,
      stage: "FINALIZE",
      status: "PENDING",
      attemptCount: 0,
      message: null,
      errorCode: null,
      startedAt: null,
      finishedAt: null,
      attempts: []
    }
  ]
};

export function taskPage(items: TaskSummary[], overrides: Partial<TaskPage> = {}): TaskPage {
  return {
    items,
    page: 1,
    size: 20,
    total: items.length,
    totalPages: items.length > 0 ? 1 : 0,
    hasNext: false,
    ...overrides
  };
}
