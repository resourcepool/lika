"use client"

import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Loader2, LogOut } from "lucide-react"
import { toast } from "sonner"
import { api } from "@/lib/client"
import { changePassword, signOutEverywhere } from "@/app/actions"
import type { Me } from "@/lib/types"
import { useDomains } from "@/hooks/use-lika"
import { useSession } from "@/components/app/session-context"
import { AccessBadges } from "@/components/access/access"
import { McpCard, type McpEndpoint } from "@/components/profile/mcp-card"
import { AuthForm } from "@/components/app/auth-form"
import { FormField } from "@/components/app/form-field"
import { PageHeader } from "@/components/app/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"

export type ExternalPasswords = { accountUrl: string | null; label: string }

export function ProfileView({ mcp, externalPasswords }: { mcp: McpEndpoint; externalPasswords: ExternalPasswords | null }) {
  const session = useSession()
  const domains = useDomains()
  const queryClient = useQueryClient()
  const [name, setName] = useState(session.user.name)
  const rename = useMutation({
    mutationFn: () => api<Me>("/api/me", { method: "PATCH", body: { name: name.trim() } }),
    onSuccess: (me) => {
      queryClient.setQueryData(["me"], me)
      toast.success("Name updated")
    },
    onError: (e) => toast.error(e.message),
  })
  const hostOf = (id: string) => domains.data?.find((d) => d.id === id)?.host

  return (
    <>
      <PageHeader title="Profile" description={session.user.email} />
      <div className="grid max-w-3xl gap-4 p-4 md:p-6">
        <Card>
          <CardHeader>
            <CardTitle>You</CardTitle>
            <CardDescription>Your email is your sign-in. Ask an admin to change it.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <form
              className="flex items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault()
                rename.mutate()
              }}
            >
              <div className="flex-1">
                <FormField label="Name" htmlFor="profile-name">
                  <Input id="profile-name" required value={name} onChange={(e) => setName(e.target.value)} />
                </FormField>
              </div>
              <Button type="submit" variant="secondary" disabled={rename.isPending || !name.trim() || name.trim() === session.user.name}>
                {rename.isPending && <Loader2 className="animate-spin" />}
                Save
              </Button>
            </form>
            <FormField label="Access">
              <AccessBadges admin={session.admin} domains={session.claims.domains} hostOf={hostOf} />
            </FormField>
          </CardContent>
        </Card>

        <McpCard mcp={mcp} />

        <Card>
          <CardHeader>
            <CardTitle>Password</CardTitle>
            <CardDescription>Your other browsers are signed out; this one stays signed in.</CardDescription>
          </CardHeader>
          <CardContent>
            {externalPasswords ? (
              <p className="text-sm text-muted-foreground">
                Your password is managed by {externalPasswords.label}.{" "}
                {externalPasswords.accountUrl && (
                  <a href={externalPasswords.accountUrl} target="_blank" rel="noreferrer" className="font-medium text-foreground underline-offset-4 hover:underline">
                    Change it in your account
                  </a>
                )}
              </p>
            ) : (
              <AuthForm
                action={changePassword}
                fields={[
                  { name: "currentPassword", label: "Current password", type: "password", autoComplete: "current-password" },
                  { name: "password", label: "New password", type: "password", autoComplete: "new-password", hint: "At least 10 characters." },
                  { name: "confirmation", label: "Confirm new password", type: "password", autoComplete: "new-password" },
                ]}
                submitLabel="Change password"
                success={<p className="text-sm">Password changed. Your other browsers were signed out.</p>}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Sessions</CardTitle>
            <CardDescription>Lost a device? Sign out of every browser, this one included.</CardDescription>
          </CardHeader>
          <CardContent>
            <form action={signOutEverywhere}>
              <Button type="submit" variant="destructive">
                <LogOut />
                Sign out everywhere
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
