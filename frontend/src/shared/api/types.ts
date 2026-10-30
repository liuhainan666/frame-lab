export type UserRole = "USER" | "ADMIN";

export type User = {
  id: number;
  email: string;
  displayName: string;
  role: UserRole;
  createdAt: string;
};

export type SessionPayload = {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: User;
};

export type FieldError = {
  field: string;
  message: string;
};

export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  traceId?: string;
  errors?: FieldError[];
};
