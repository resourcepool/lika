import type { Metadata } from "next"
import Link from "next/link"
import { forgotPassword } from "@/app/actions"
import { authConfig } from "@/lib/auth"
import { AuthShell } from "@/components/app/auth-shell"
import { AuthForm } from "@/components/app/auth-form"
import { SsoButton } from "@/components/app/sso-button"

export const metadata: Metadata = { title: "Forgot password" }

export default async function ForgotPasswordPage() {
  const config = await authConfig()
  const back = (
    <Link href="/signin" className="hover:text-foreground">
      Back to sign in
    </Link>
  )
  if (config?.passwordReset === "provider") {
    return (
      <AuthShell title="Forgot your password?" footer={back}>
        <p className="text-sm text-muted-foreground">Your password is managed by {config.sso?.label ?? "your identity provider"}: use its &quot;Forgot password&quot; link on its sign-in page.</p>
        {config.sso && <SsoButton sso={config.sso} />}
      </AuthShell>
    )
  }
  if (config?.passwordReset === "admin") {
    return (
      <AuthShell title="Forgot your password?" footer={back}>
        <p className="text-sm text-muted-foreground">This Lika does not send emails. Ask an admin for a password reset link.</p>
      </AuthShell>
    )
  }
  return (
    <AuthShell title="Forgot your password?" description="We will email you a link to choose a new one." footer={back}>
      <AuthForm
        action={forgotPassword}
        fields={[{ name: "email", label: "Email", type: "email", autoComplete: "username" }]}
        submitLabel="Send the link"
        success={<p className="text-sm">If an account exists for this email, a link to reset your password is on its way.</p>}
      />
    </AuthShell>
  )
}
