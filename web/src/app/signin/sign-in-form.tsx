"use client"

import { useActionState } from "react"
import { KeyRound, Loader2 } from "lucide-react"
import { signIn, type SignInState } from "@/app/actions"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Alert, AlertDescription } from "@/components/ui/alert"

export function SignInForm({ expired }: { expired: boolean }) {
  const [state, action, pending] = useActionState<SignInState, FormData>(signIn, {})
  const error = state.error ?? (expired ? "Your key was revoked or regenerated. Sign in again." : undefined)
  return (
    <form action={action} className="space-y-4 rounded-xl border bg-card/80 p-6 shadow-2xl backdrop-blur">
      <div className="space-y-2">
        <Label htmlFor="token">API key</Label>
        <div className="relative">
          <KeyRound className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            id="token"
            name="token"
            type="password"
            autoComplete="off"
            autoFocus
            required
            placeholder="••••••••••••••••"
            className="pl-9 font-mono"
          />
        </div>
      </div>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <Button type="submit" className="w-full" disabled={pending}>
        {pending && <Loader2 className="animate-spin" />}
        Sign in
      </Button>
    </form>
  )
}
