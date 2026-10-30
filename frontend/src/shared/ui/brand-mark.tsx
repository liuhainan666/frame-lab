export function BrandMark({ className }: { className?: string }) {
  return (
    <svg
      aria-hidden
      focusable="false"
      viewBox="0 0 28 28"
      className={className}
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M6 23V5h16" />
      <path d="M6 14h11" />
      <path d="M6 5l16 18" opacity="0.55" />
    </svg>
  );
}
