"use client"

import { useActionState } from "react"
import { Loader2 } from "lucide-react"
import type { FormState } from "@/app/actions"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

type Field = {
  name: string
  label: string
  type?: "text" | "email" | "password"
  autoComplete?: string
  defaultValue?: string
  readOnly?: boolean
  required?: boolean
  hint?: string
}

/**
 * Form posting to a server action, for the pages shown before signing in and the profile.
 */
export function AuthForm({
  action,
  fields,
  submitLabel,
  hidden,
  notice,
  success,
}: {
  action: (state: FormState, formData: FormData) => Promise<FormState>
  fields: Field[]
  submitLabel: string
  hidden?: Record<string, string>
  /** Shown above the form, e.g. why the user was sent here */
  notice?: string
  /** Replaces the form once the action is done */
  success?: React.ReactNode
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(action, {})
  if (state.done && success) {
    return <>{success}</>
  }
  return (
    <form action={formAction} className="space-y-4">
      {notice && !state.error && (
        <Alert>
          <AlertDescription>{notice}</AlertDescription>
        </Alert>
      )}
      {Object.entries(hidden ?? {}).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      {fields.map((f, i) => (
        <div key={f.name} className="space-y-1.5">
          <Label htmlFor={f.name}>{f.label}</Label>
          <Input
            id={f.name}
            name={f.name}
            type={f.type ?? "text"}
            autoComplete={f.autoComplete}
            defaultValue={f.defaultValue}
            readOnly={f.readOnly}
            required={f.required ?? true}
            autoFocus={i === fields.findIndex((x) => !x.readOnly)}
            className={f.type === "password" ? "font-mono" : undefined}
          />
          {f.hint && <p className="text-xs text-muted-foreground">{f.hint}</p>}
        </div>
      ))}
      {state.error && (
        <Alert variant="destructive">
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      )}
      <Button type="submit" className="w-full" disabled={pending}>
        {pending && <Loader2 className="animate-spin" />}
        {submitLabel}
      </Button>
    </form>
  )
}
