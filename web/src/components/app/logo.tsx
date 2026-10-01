import { cn } from "@/lib/utils"

/** Two chain links joined by the accent stroke */
export function Logo({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" fill="none" className={cn("size-6", className)} aria-label="Lika">
      <rect x="1" y="1" width="30" height="30" rx="8" className="fill-foreground/5 stroke-border" />
      <path d="M13.5 18.5 18.5 13.5" className="stroke-brand" strokeWidth="2.5" strokeLinecap="round" />
      <path
        d="M14.8 10.6 16.2 9.2a4.2 4.2 0 0 1 5.9 5.9l-1.4 1.4M17.2 21.4l-1.4 1.4a4.2 4.2 0 0 1-5.9-5.9l1.4-1.4"
        className="stroke-foreground"
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  )
}
