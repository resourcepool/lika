import { z } from "zod"
import { fromDateTimeLocal, toDateTimeLocal } from "@/lib/link-status"
import type { Link, LinkInput } from "@/lib/types"

export const linkFormSchema = z
  .object({
    targetUrl: z.url({ message: "Enter a full URL, e.g. https://example.com" }),
    domainId: z.string().min(1, "Choose a domain"),
    slug: z
      .string()
      .trim()
      .regex(/^[A-Za-z0-9_-]*$/, "Letters, digits, - and _ only"),
    passwordMode: z.enum(["none", "keep", "set", "remove"]),
    password: z.string(),
    scheduled: z.boolean(),
    activationInstant: z.string(),
    targetUrlBeforeActivation: z.union([z.literal(""), z.url({ message: "Enter a full URL" })]),
    expires: z.boolean(),
    expiryDate: z.string(),
    metadata: z.array(z.object({ key: z.string(), value: z.string() })),
  })
  .superRefine((v, ctx) => {
    if (v.passwordMode === "set" && !v.password) {
      ctx.addIssue({ code: "custom", path: ["password"], message: "Enter a password" })
    }
    if (v.scheduled && !v.activationInstant) {
      ctx.addIssue({ code: "custom", path: ["activationInstant"], message: "Choose when the link activates" })
    }
    if (v.expires && !v.expiryDate) {
      ctx.addIssue({ code: "custom", path: ["expiryDate"], message: "Choose the last day of the link" })
    }
  })

export type LinkFormValues = z.infer<typeof linkFormSchema>

export function emptyLinkForm(domainId: string): LinkFormValues {
  return {
    targetUrl: "",
    domainId,
    slug: "",
    passwordMode: "none",
    password: "",
    scheduled: false,
    activationInstant: "",
    targetUrlBeforeActivation: "",
    expires: false,
    expiryDate: "",
    metadata: [],
  }
}

export function linkToForm(link: Link, domainId: string): LinkFormValues {
  const activation = link.options.activation
  return {
    targetUrl: link.targetUrl,
    domainId,
    slug: link.slug,
    passwordMode: link.options.password?.enabled ? "keep" : "none",
    password: "",
    scheduled: !!activation?.activationInstant,
    activationInstant: toDateTimeLocal(activation?.activationInstant),
    targetUrlBeforeActivation: activation?.targetUrlBeforeActivation ?? "",
    expires: !!link.options.expiry?.expiryDate,
    expiryDate: link.options.expiry?.expiryDate ?? "",
    metadata: Object.entries(link.metadata ?? {}).map(([key, value]) => ({ key, value })),
  }
}

/**
 * Builds the API payload. The password is only sent when it changes:
 * omitting it keeps the current one. Fields the form does not show
 * (type, landing template) are kept from the existing link.
 */
export function formToLinkInput(values: LinkFormValues, existing?: Link): LinkInput {
  const options: LinkInput["options"] = {}
  if (existing?.options.landing?.templateId) {
    options.landing = { templateId: existing.options.landing.templateId }
  }
  if (values.scheduled) {
    options.activation = {
      activationInstant: fromDateTimeLocal(values.activationInstant),
      ...(values.targetUrlBeforeActivation ? { targetUrlBeforeActivation: values.targetUrlBeforeActivation } : {}),
    }
  }
  if (values.expires) {
    options.expiry = { expiryDate: values.expiryDate }
  }
  if (values.passwordMode === "set") {
    options.password = { password: values.password }
  } else if (values.passwordMode === "remove") {
    options.password = { remove: true }
  }
  return {
    ...(existing ? { id: existing.id } : {}),
    targetUrl: values.targetUrl.trim(),
    domainId: values.domainId,
    ...(values.slug.trim() ? { slug: values.slug.trim() } : {}),
    type: existing?.type ?? "SIMPLE",
    options,
    metadata: Object.fromEntries(values.metadata.filter((m) => m.key.trim()).map((m) => [m.key.trim(), m.value])),
  }
}
