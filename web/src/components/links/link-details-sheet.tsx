"use client"

import { format } from "date-fns"
import { ExternalLink, Pencil } from "lucide-react"
import type { Link } from "@/lib/types"
import { useApiKeys, useUsers } from "@/hooks/use-lika"
import { useSession } from "@/components/app/session-context"
import { CopyButton } from "@/components/app/copy-button"
import { LinkStatusBadges } from "@/components/links/link-status-badge"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"

export function LinkDetailsSheet({ link, onOpenChange, onEdit }: { link?: Link; onOpenChange: (open: boolean) => void; onEdit: (link: Link) => void }) {
  const session = useSession()
  const users = useUsers(session.canManage && !!link)
  const keys = useApiKeys(session.canManage && !!link)
  const creator =
    link &&
    (link.createdBy === session.user.id
      ? "You"
      : link.createdBy === "system"
        ? "Configuration"
        : (users.data?.find((u) => u.id === link.createdBy)?.name ?? keys.data?.find((k) => k.id === link.createdBy)?.name ?? "Someone else"))
  const activation = link?.options.activation

  return (
    <Sheet open={!!link} onOpenChange={onOpenChange}>
      <SheetContent className="w-full gap-0 sm:max-w-md">
        {link && (
          <>
            <SheetHeader>
              <SheetTitle className="font-mono">/{link.slug}</SheetTitle>
              <SheetDescription className="font-mono text-xs">{link.host}</SheetDescription>
            </SheetHeader>
            <div className="space-y-5 overflow-y-auto px-4 pb-4 text-sm">
              <LinkStatusBadges link={link} />
              <Row label="Short URL">
                <span className="flex items-center gap-1">
                  <span className="truncate font-mono text-xs">{link.shortenedUrl}</span>
                  <CopyButton value={link.shortenedUrl} />
                </span>
              </Row>
              <Row label="Destination">
                <a href={link.targetUrl} target="_blank" rel="noreferrer" className="inline-flex items-start gap-1 font-mono text-xs break-all hover:text-brand">
                  {link.targetUrl}
                  <ExternalLink className="mt-0.5 size-3 shrink-0" />
                </a>
              </Row>
              <Separator />
              <Row label="Created">{format(new Date(link.createdDateTime), "PPpp")}</Row>
              <Row label="Updated">{format(new Date(link.lastUpdateDateTime), "PPpp")}</Row>
              <Row label="Created by">
                <span className="font-mono text-xs">{creator}</span>
              </Row>
              {(activation?.activationInstant || link.options.expiry?.expiryDate) && <Separator />}
              {activation?.activationInstant && (
                <Row label="Activates">
                  {format(new Date(activation.activationInstant), "PPpp")}
                  {activation.targetUrlBeforeActivation && (
                    <span className="block font-mono text-xs break-all text-muted-foreground">until then → {activation.targetUrlBeforeActivation}</span>
                  )}
                </Row>
              )}
              {link.options.expiry?.expiryDate && <Row label="Last day">{format(new Date(`${link.options.expiry.expiryDate}T00:00`), "PP")}</Row>}
              {Object.keys(link.metadata ?? {}).length > 0 && (
                <>
                  <Separator />
                  <Row label="Metadata">
                    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 font-mono text-xs">
                      {Object.entries(link.metadata).map(([key, value]) => (
                        <div key={key} className="contents">
                          <dt className="text-muted-foreground">{key}</dt>
                          <dd className="break-all">{value}</dd>
                        </div>
                      ))}
                    </dl>
                  </Row>
                </>
              )}
            </div>
            <SheetFooter>
              <Button onClick={() => onEdit(link)}>
                <Pencil />
                Edit
              </Button>
            </SheetFooter>
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">{label}</p>
      <div>{children}</div>
    </div>
  )
}
