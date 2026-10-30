import { Loader2 } from "lucide-react";
import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";

import { cn } from "@/shared/lib/cn";

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "icon";
type ButtonSize = "sm" | "md" | "lg";

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  leadingIcon?: ReactNode;
};

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    "bg-accent text-accent-ink shadow-[0_8px_24px_-12px_rgba(196,229,82,0.55)] hover:bg-accent-hover active:bg-accent-pressed",
  secondary:
    "border border-line-strong bg-transparent text-ink hover:border-accent-border hover:bg-surface-hover",
  ghost: "bg-transparent text-ink-secondary hover:bg-surface-hover hover:text-ink",
  danger: "border border-danger/25 bg-danger-soft text-danger hover:border-danger/45",
  icon: "bg-transparent text-ink-secondary hover:bg-surface-hover hover:text-ink"
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-[12px]",
  md: "h-10 px-4 text-[13px]",
  lg: "h-12 px-6 text-sm"
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    className,
    variant = "primary",
    size = "md",
    loading = false,
    leadingIcon,
    disabled,
    children,
    type = "button",
    ...props
  },
  ref
) {
  const iconOnly = variant === "icon";
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-full font-medium",
        "transition-[background-color,border-color,color,transform,box-shadow] duration-150",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg",
        "disabled:cursor-not-allowed disabled:opacity-45",
        iconOnly ? "h-9 w-9 p-0" : sizeClasses[size],
        variantClasses[variant],
        className
      )}
      {...props}
    >
      {loading ? <Loader2 aria-hidden className="h-4 w-4 animate-spin" /> : leadingIcon}
      {!iconOnly && children}
    </button>
  );
});
