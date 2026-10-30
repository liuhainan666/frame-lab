import type { FieldError, ProblemDetail } from "./types";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail?: string;
  readonly fieldErrors: FieldError[];
  readonly traceId?: string;

  constructor(
    status: number,
    code: string,
    message: string,
    options?: { detail?: string; fieldErrors?: FieldError[]; traceId?: string }
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.detail = options?.detail;
    this.fieldErrors = options?.fieldErrors ?? [];
    this.traceId = options?.traceId;
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((error) => error.field === field)?.message;
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError;
}

export async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail | undefined;
  try {
    const contentType = response.headers.get("content-type") ?? "";
    if (contentType.includes("json")) {
      problem = (await response.json()) as ProblemDetail;
    }
  } catch {
    problem = undefined;
  }
  const code = problem?.code ?? (response.status === 0 ? "NETWORK_ERROR" : "INTERNAL_ERROR");
  const message = problem?.title ?? problem?.detail ?? response.statusText ?? code;
  return new ApiError(response.status, code, message, {
    detail: problem?.detail,
    fieldErrors: problem?.errors,
    traceId: problem?.traceId
  });
}

export function networkError(): ApiError {
  return new ApiError(0, "NETWORK_ERROR", "Network request failed");
}
