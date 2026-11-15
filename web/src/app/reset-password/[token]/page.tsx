import type { Metadata } from "next"
import Link from "next/link"
import { resetPassword } from "@/app/actions"
import { PASSWORD_MIN_LENGTH } from "@/lib/auth"
import { AuthShell } from "@/components/app/auth-shell"
import { AuthForm } from "@/components/app/auth-form"

export const metadata: Metadata = { title: "Choose a new password" }

export default async function ResetPasswordPage({ params }: PageProps<"/reset-password/[token]">) {
  const { token } = await params
  return (
    <AuthShell
      title="Choose a new password"
      description="You will be signed out of every browser."
      footer={
        <Link href="/forgot-password" className="hover:text-foreground">
          Link expired? Ask for a new one
        </Link>
      }
    >
      <AuthForm
        action={resetPassword.bind(null, token)}
        fields={[
          { name: "password", label: "New password", type: "password", autoComplete: "new-password", hint: `At least ${PASSWORD_MIN_LENGTH} characters.` },
          { name: "confirmation", label: "Confirm password", type: "password", autoComplete: "new-password" },
        ]}
        submitLabel="Change password"
      />
    </AuthShell>
  )
}
