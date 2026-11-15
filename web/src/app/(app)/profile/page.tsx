import type { Metadata } from "next"
import { authConfig } from "@/lib/auth"
import { apiUrl } from "@/lib/env"
import { ProfileView } from "@/components/profile/profile-view"

export const metadata: Metadata = { title: "Profile" }

export default async function ProfilePage() {
  const config = await authConfig()
  // Without LIKA_PUBLIC_API_URL on the API, the URL this server uses may not be reachable from outside
  const mcp = config?.mcpUrl ? { url: config.mcpUrl, configured: true } : { url: `${apiUrl()}/mcp`, configured: false }
  // Passwords are changed in Lika, or at the identity provider (Keycloak)
  const passwords = config && !config.passwordSignIn ? { accountUrl: config.accountUrl, label: config.sso?.label ?? "your identity provider" } : null
  return <ProfileView mcp={mcp} externalPasswords={passwords} />
}
