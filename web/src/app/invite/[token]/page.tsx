import type { Metadata } from "next"
import Link from "next/link"
import { acceptInvitation } from "@/app/actions"
import { PASSWORD_MIN_LENGTH, authConfig } from "@/lib/auth"
import { likaJson } from "@/lib/lika-api"
import type { InvitationInfo } from "@/lib/types"
import { AuthShell } from "@/components/app/auth-shell"
import { AuthForm } from "@/components/app/auth-form"

export const metadata: Metadata = { title: "Accept the invitation" }

export default async function InvitePage({ params }: PageProps<"/invite/[token]">) {
  const { token } = await params
  const [invitation, config] = await Promise.all([likaJson<InvitationInfo>(`/api/auth/invitations/${encodeURIComponent(token)}`).catch(() => null), authConfig()])
  if (!invitation?.ok) {
    return (
      <AuthShell title="Invitation expired" description="This invitation link was already used, replaced by a newer one, or has expired.">
        <p className="text-sm text-muted-foreground">Ask the person who invited you to send a new invitation.</p>
        <Link href="/signin" className="block text-center text-sm font-medium hover:text-brand">
          Go to sign in
        </Link>
      </AuthShell>
    )
  }
  const { email, name, accountExists } = invitation.data
  const providerName = config?.sso?.label ?? (config?.provider === "firebase" ? "Firebase" : "your identity provider")
  if (accountExists) {
    return (
      <AuthShell title="Join Lika" description={`You already have an account with ${email}: accept, then sign in with it.`}>
        <AuthForm
          action={acceptInvitation.bind(null, token)}
          hidden={{ name, password: "", confirmation: "" }}
          fields={[{ name: "email", label: "Email", type: "email", defaultValue: email, readOnly: true, autoComplete: "username" }]}
          submitLabel="Accept the invitation"
        />
        <p className="text-xs text-muted-foreground">Your {providerName} password stays the same.</p>
      </AuthShell>
    )
  }
  return (
    <AuthShell title="Join Lika" description="Choose a password to accept the invitation.">
      <AuthForm
        action={acceptInvitation.bind(null, token)}
        fields={[
          { name: "email", label: "Email", type: "email", defaultValue: email, readOnly: true, autoComplete: "username" },
          { name: "name", label: "Your name", defaultValue: name, autoComplete: "name" },
          { name: "password", label: "Password", type: "password", autoComplete: "new-password", hint: `At least ${PASSWORD_MIN_LENGTH} characters.` },
          { name: "confirmation", label: "Confirm password", type: "password", autoComplete: "new-password" },
        ]}
        submitLabel={config?.passwordSignIn === false ? `Create my ${providerName} account` : "Accept and sign in"}
      />
    </AuthShell>
  )
}
