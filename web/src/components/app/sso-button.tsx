import { LogIn } from "lucide-react"
import type { SsoConfig } from "@/lib/types"
import { Button } from "@/components/ui/button"

/** Starts single sign-on with the identity provider (a plain link: the route handler redirects) */
export function SsoButton({ sso, next, variant = "default" }: { sso: SsoConfig; next?: string; variant?: "default" | "outline" }) {
  const href = next ? `/auth/sso?next=${encodeURIComponent(next)}` : "/auth/sso"
  return (
    <Button asChild variant={variant} className="w-full">
      <a href={href}>
        <LogIn />
        Sign in with {sso.label}
      </a>
    </Button>
  )
}
