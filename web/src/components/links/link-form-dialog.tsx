"use client"

import { useEffect, useState } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Check, Loader2, Lock, X } from "lucide-react"
import { toast } from "sonner"
import { api, ApiError } from "@/lib/client"
import { emptyLinkForm, formToLinkInput, linkFormSchema, linkToForm, type LinkFormValues } from "@/lib/link-form"
import type { Domain, Link } from "@/lib/types"
import { MetadataEditor } from "@/components/links/metadata-editor"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Switch } from "@/components/ui/switch"

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  domains: Domain[]
  defaultDomainId: string
  /** Edited link, or undefined to create one */
  link?: Link
}

export function LinkFormDialog({ open, onOpenChange, domains, defaultDomainId, link }: Props) {
  const queryClient = useQueryClient()
  const editing = !!link
  const linkDomainId = link ? (domains.find((d) => d.host === link.host)?.id ?? "") : defaultDomainId

  const form = useForm<LinkFormValues>({
    resolver: zodResolver(linkFormSchema),
    defaultValues: link ? linkToForm(link, linkDomainId) : emptyLinkForm(defaultDomainId),
  })
  const { register, control, handleSubmit, reset, setValue, formState } = form
  const errors = formState.errors

  useEffect(() => {
    if (open) {
      reset(link ? linkToForm(link, linkDomainId) : emptyLinkForm(defaultDomainId))
    }
  }, [open, link, linkDomainId, defaultDomainId, reset])

  const save = useMutation({
    mutationFn: (values: LinkFormValues) => {
      const body = formToLinkInput(values, link)
      return api<Link>("/api/links", { method: editing ? "PUT" : "POST", body })
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ["links"] })
      toast.success(editing ? "Link updated" : "Link created", { description: saved.shortenedUrl })
      onOpenChange(false)
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) {
        form.setError("slug", { message: "This slug is already taken" })
      } else {
        toast.error(error.message)
      }
    },
  })

  const [domainId, slug, passwordMode, scheduled, expires] = useWatch({
    control,
    name: ["domainId", "slug", "passwordMode", "scheduled", "expires"],
  })
  const domain = domains.find((d) => d.id === domainId)
  const slugCheck = useSlugAvailability(editing ? "" : slug, domainId)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{editing ? "Edit link" : "New link"}</DialogTitle>
          <DialogDescription>
            {editing ? "The short URL cannot change once published." : "Leave the slug empty to generate one."}
          </DialogDescription>
        </DialogHeader>

        <form id="link-form" onSubmit={handleSubmit((v) => save.mutate(v))} className="space-y-5">
          <Field label="Destination" htmlFor="targetUrl" error={errors.targetUrl?.message}>
            <Input id="targetUrl" placeholder="https://example.com/a/very/long/url" className="font-mono text-sm" {...register("targetUrl")} />
          </Field>

          <div className="grid gap-4 sm:grid-cols-[1fr_1fr]">
            <Field label="Domain" error={errors.domainId?.message}>
              <Controller
                control={control}
                name="domainId"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange} disabled={editing}>
                    <SelectTrigger className="w-full font-mono text-xs" aria-label="Domain">
                      <SelectValue placeholder="Choose a domain" />
                    </SelectTrigger>
                    <SelectContent>
                      {domains.map((d) => (
                        <SelectItem key={d.id} value={d.id} className="font-mono text-xs">
                          {d.host}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>
            <Field label="Slug" htmlFor="slug" error={errors.slug?.message}>
              <div className="relative">
                <Input id="slug" placeholder="auto" disabled={editing} className="pr-8 font-mono text-sm" {...register("slug")} />
                <span className="absolute top-1/2 right-2.5 -translate-y-1/2">
                  {slugCheck.state === "checking" && <Loader2 className="size-4 animate-spin text-muted-foreground" />}
                  {slugCheck.state === "available" && <Check className="size-4 text-brand" aria-label="Available" />}
                  {slugCheck.state === "taken" && <X className="size-4 text-destructive" aria-label="Taken" />}
                </span>
              </div>
            </Field>
          </div>
          {domain && (
            <p className="-mt-2 truncate font-mono text-xs text-muted-foreground">
              {domain.apiBaseUrl}/<span className="text-foreground">{slug || "…"}</span>
            </p>
          )}
          {slugCheck.state === "taken" && slugCheck.suggestions.length > 0 && (
            <div className="-mt-2 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
              Taken. Try
              {slugCheck.suggestions.map((s) => (
                <button
                  key={s}
                  type="button"
                  className="rounded border px-1.5 py-0.5 font-mono text-foreground hover:border-brand"
                  onClick={() => setValue("slug", s, { shouldValidate: true })}
                >
                  {s}
                </button>
              ))}
            </div>
          )}

          <Separator />

          <Section title="Password" description="Visitors must enter it before being redirected.">
            {passwordMode === "keep" ? (
              <div className="flex flex-wrap items-center gap-2">
                <span className="inline-flex items-center gap-1.5 text-sm">
                  <Lock className="size-4 text-brand" /> Password set
                </span>
                <Button type="button" size="sm" variant="outline" onClick={() => setValue("passwordMode", "set")}>
                  Replace
                </Button>
                <Button type="button" size="sm" variant="ghost" onClick={() => setValue("passwordMode", "remove")}>
                  Remove
                </Button>
              </div>
            ) : passwordMode === "remove" ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                The password will be removed.
                <Button type="button" size="sm" variant="ghost" onClick={() => setValue("passwordMode", "keep")}>
                  Undo
                </Button>
              </div>
            ) : (
              <Field error={errors.password?.message}>
                <Input
                  type="password"
                  autoComplete="new-password"
                  placeholder={link?.options.password?.enabled ? "New password" : "No password"}
                  {...register("password", {
                    onChange: (e) => setValue("passwordMode", e.target.value ? "set" : link?.options.password?.enabled ? "keep" : "none"),
                  })}
                />
              </Field>
            )}
          </Section>

          <Section
            title="Scheduled activation"
            description="Until then, visitors go to the fallback URL, or get a 404."
            toggle={<Controller control={control} name="scheduled" render={({ field }) => <Switch checked={field.value} onCheckedChange={field.onChange} aria-label="Scheduled activation" />} />}
          >
            {scheduled && (
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Activates at" htmlFor="activationInstant" error={errors.activationInstant?.message}>
                  <Input id="activationInstant" type="datetime-local" className="font-mono text-sm" {...register("activationInstant")} />
                </Field>
                <Field label="Fallback URL" htmlFor="targetUrlBeforeActivation" error={errors.targetUrlBeforeActivation?.message}>
                  <Input id="targetUrlBeforeActivation" placeholder="optional" className="font-mono text-sm" {...register("targetUrlBeforeActivation")} />
                </Field>
              </div>
            )}
          </Section>

          <Section
            title="Expiry"
            description="The link stops working after this day, and is deleted the next night."
            toggle={<Controller control={control} name="expires" render={({ field }) => <Switch checked={field.value} onCheckedChange={field.onChange} aria-label="Expiry" />} />}
          >
            {expires && (
              <Field label="Last day" htmlFor="expiryDate" error={errors.expiryDate?.message}>
                <Input id="expiryDate" type="date" className="w-48 font-mono text-sm" {...register("expiryDate")} />
              </Field>
            )}
          </Section>

          <Section title="Metadata" description="Free key/value pairs, e.g. a campaign name.">
            <Controller control={control} name="metadata" render={({ field }) => <MetadataEditor rows={field.value} onChange={field.onChange} />} />
          </Section>
        </form>

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="link-form" disabled={save.isPending}>
            {save.isPending && <Loader2 className="animate-spin" />}
            {editing ? "Save" : "Create link"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Field({ label, htmlFor, error, children }: { label?: string; htmlFor?: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      {label && <Label htmlFor={htmlFor}>{label}</Label>}
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}

function Section({ title, description, toggle, children }: { title: string; description: string; toggle?: React.ReactNode; children?: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="text-sm font-medium">{title}</h3>
          <p className="text-xs text-muted-foreground">{description}</p>
        </div>
        {toggle}
      </div>
      {children}
    </section>
  )
}

type SlugCheck = { state: "idle" | "checking" | "available" | "taken"; suggestions: string[] }

/** Debounced availability check, with suggestions when the slug is taken */
function useSlugAvailability(slug: string, domainId: string): SlugCheck {
  const value = slug.trim()
  const checkable = !!value && !!domainId && /^[A-Za-z0-9_-]+$/.test(value)
  const key = `${domainId}/${value}`
  const [result, setResult] = useState<SlugCheck & { key: string }>()

  useEffect(() => {
    if (!checkable) return
    let cancelled = false
    const timer = setTimeout(async () => {
      try {
        await api(`/api/links/slug/${encodeURIComponent(value)}`, { query: { domainId } })
        if (!cancelled) setResult({ key, state: "available", suggestions: [] })
      } catch (error) {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 409) {
          const suggestions = await api<string[]>(`/api/search/slug/suggestions/${encodeURIComponent(value)}`, { query: { domainId } }).catch(() => [])
          if (!cancelled) setResult({ key, state: "taken", suggestions: suggestions.filter((s) => s !== value) })
        } else {
          setResult({ key, state: "idle", suggestions: [] })
        }
      }
    }, 400)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [checkable, key, value, domainId])

  if (!checkable) return { state: "idle", suggestions: [] }
  return result?.key === key ? result : { state: "checking", suggestions: [] }
}
