import { Clock, Lock, TimerOff } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { linkStatus } from "@/lib/link-status"
import type { Link } from "@/lib/types"

export function LinkStatusBadges({ link }: { link: Link }) {
  const status = linkStatus(link)
  return (
    <div className="flex flex-wrap items-center gap-1">
      {status === "active" && (
        <Badge variant="outline" className="gap-1.5 font-mono text-[11px]">
          <span className="size-1.5 rounded-full bg-brand shadow-[0_0_6px_var(--brand)]" />
          live
        </Badge>
      )}
      {status === "scheduled" && (
        <Badge variant="outline" className="gap-1 font-mono text-[11px] text-amber-500">
          <Clock className="size-3" />
          scheduled
        </Badge>
      )}
      {status === "expired" && (
        <Badge variant="outline" className="gap-1 font-mono text-[11px] text-muted-foreground">
          <TimerOff className="size-3" />
          expired
        </Badge>
      )}
      {link.options.password?.enabled && (
        <Badge variant="outline" className="gap-1 font-mono text-[11px]">
          <Lock className="size-3" />
          protected
        </Badge>
      )}
    </div>
  )
}
