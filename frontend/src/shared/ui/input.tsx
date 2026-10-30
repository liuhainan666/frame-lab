import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type ReactNode
} from "react";

import { cn } from "@/shared/lib/cn";

export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: string;
  endAdornment?: ReactNode;
};

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, hint, endAdornment, className, id, "aria-describedby": ariaDescribedBy, ...props },
  ref
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const describedBy = [ariaDescribedBy, hintId, errorId].filter(Boolean).join(" ") || undefined;

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="text-[13px] font-medium text-ink">
        {label}
      </label>
      <div className="relative">
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            "h-10 w-full rounded-[var(--radius-sm)] border border-line bg-bg-elevated px-3 text-sm text-ink",
            "placeholder:text-ink-disabled",
            "transition-colors duration-150 hover:border-line-strong",
            "focus:border-accent-border focus:outline-none focus:ring-2 focus:ring-accent/20",
            "disabled:cursor-not-allowed disabled:opacity-55",
            error && "border-danger/55 focus:border-danger focus:ring-danger/20",
            endAdornment && "pr-11",
            className
          )}
          {...props}
        />
        {endAdornment ? (
          <div className="absolute inset-y-0 right-1 flex items-center">{endAdornment}</div>
        ) : null}
      </div>
      {hint && !error ? (
        <p id={hintId} className="text-[12px] leading-[18px] text-ink-muted">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} role="alert" className="text-[12px] leading-[18px] text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
});
