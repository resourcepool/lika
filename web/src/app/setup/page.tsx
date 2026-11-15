import type { Metadata } from "next"
import { redirect } from "next/navigation"
import { setup } from "@/app/actions"
import { PASSWORD_MIN_LENGTH, authConfig } from "@/lib/auth"
import { AuthShell } from "@/components/app/auth-shell"
import { AuthForm } from "@/components/app/auth-form"

export const metadata: Metadata = { title: "Set up Lika" }

export default async function SetupPage() {
  const config = await authConfig()
  if (config && !config.setupRequired) {
    redirect("/signin")
  }
  const external = config && config.provider !== "local"
  const providerName = config?.sso?.label ?? (config?.provider === "firebase" ? "Firebase" : "the identity provider")
  return (
    <AuthShell
      title="Welcome to Lika"
      description={
        <>
          Create the first admin account. The setup token is printed in the logs of the Lika API, e.g.{" "}
          <code className="font-mono text-xs text-foreground">docker logs lika-api</code>.
        </>
      }
    >
      <AuthForm
        action={setup}
        fields={[
          { name: "setupToken", label: "Setup token", autoComplete: "off" },
          { name: "name", label: "Your name", autoComplete: "name", required: false },
          { name: "email", label: "Email", type: "email", autoComplete: "username" },
          {
            name: "password",
            label: "Password",
            type: "password",
            autoComplete: "new-password",
            required: !external,
            hint: external
              ? `At least ${PASSWORD_MIN_LENGTH} characters. Leave empty if you already have a ${providerName} account with this email.`
              : `At least ${PASSWORD_MIN_LENGTH} characters.`,
          },
          { name: "confirmation", label: "Confirm password", type: "password", autoComplete: "new-password", required: !external },
        ]}
        submitLabel="Create admin account"
      />
    </AuthShell>
  )
}
