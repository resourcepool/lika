import type { Metadata } from "next"
import Link from "next/link"
import { redirect } from "next/navigation"
import { signIn } from "@/app/actions"
import { authConfig } from "@/lib/auth"
import { getSession } from "@/lib/session"
import { AuthShell } from "@/components/app/auth-shell"
import { AuthForm } from "@/components/app/auth-form"
import { SsoButton } from "@/components/app/sso-button"
import { Alert, AlertDescription } from "@/components/ui/alert"

export const metadata: Metadata = { title: "Sign in" }

const ERRORS: Record<string, string> = {
  "not-invited": "This account has no access to Lika. Ask an admin for an invitation.",
  "user-disabled": "Your access to Lika was disabled. Ask an admin.",
  "sso-cancelled": "Sign-in was cancelled.",
  "sso-expired": "Sign-in took too long. Try again.",
  "sso-unavailable": "Single sign-on is not configured.",
  "api-unreachable": "The Lika API is unreachable.",
}

const NOTICES: Record<string, string> = {
  expired: "Your session has ended. Sign in again.",
  reset: "Your password was changed. Sign in with the new one.",
  joined: "Welcome to Lika. Sign in to continue.",
}

export default async function SignInPage({ searchParams }: PageProps<"/signin">) {
  const { expired, reset, joined, next, error } = await searchParams
  // After a revocation the cookie may still be there, holding dead tokens
  if (expired !== "1" && (await getSession())) {
    redirect("/links")
  }
  const config = await authConfig()
  if (config?.setupRequired) {
    redirect("/setup")
  }
  const target = typeof next === "string" ? next : "/links"
  const notice = expired === "1" ? NOTICES.expired : reset === "1" ? NOTICES.reset : joined === "1" ? NOTICES.joined : undefined
  const errorMessage = typeof error === "string" ? (ERRORS[error] ?? "Sign-in failed. Try again.") : undefined
  return (
    <AuthShell
      title="Sign in to Lika"
      description="You stay signed in on this browser."
      footer={
        <>
          {config?.passwordSignIn && (
            <Link href="/forgot-password" className="hover:text-foreground">
              Forgot your password?
            </Link>
          )}
          <p className="mt-2 font-mono text-xs">No account? Ask an admin of your domain for an invitation.</p>
        </>
      }
    >
      {!config && <p className="text-sm text-destructive">The Lika API is unreachable.</p>}
      {errorMessage && (
        <Alert variant="destructive">
          <AlertDescription>{errorMessage}</AlertDescription>
        </Alert>
      )}
      {config?.sso && !config.passwordSignIn && notice && (
        <Alert>
          <AlertDescription>{notice}</AlertDescription>
        </Alert>
      )}
      {config?.sso && <SsoButton sso={config.sso} next={target} variant={config.passwordSignIn ? "outline" : "default"} />}
      {config?.sso && config.passwordSignIn && (
        <div className="flex items-center gap-3 text-xs text-muted-foreground">
          <span className="h-px flex-1 bg-border" />
          or
          <span className="h-px flex-1 bg-border" />
        </div>
      )}
      {(!config || config.passwordSignIn) && (
        <AuthForm
          action={signIn}
          notice={notice}
          hidden={{ next: target }}
          fields={[
            { name: "email", label: "Email", type: "email", autoComplete: "username" },
            { name: "password", label: "Password", type: "password", autoComplete: "current-password" },
          ]}
          submitLabel="Sign in"
        />
      )}
    </AuthShell>
  )
}
