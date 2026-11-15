import type { SsoConfig } from "@/lib/types"

/** Cookie holding the PKCE verifier and the state of a sign-in in progress */
export const SSO_COOKIE = "lika_sso"
export const SSO_COOKIE_MAX_AGE = 10 * 60
export const CALLBACK_PATH = "/auth/callback"

export type SsoAttempt = { state: string; verifier: string; redirectUri: string; next: string }

function base64url(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

export function randomToken(bytes = 32): string {
  return base64url(crypto.getRandomValues(new Uint8Array(bytes)))
}

/** S256 code challenge of a PKCE verifier (RFC 7636) */
export async function codeChallenge(verifier: string): Promise<string> {
  return base64url(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier))))
}

export async function authorizationUrl(sso: SsoConfig, attempt: SsoAttempt): Promise<string> {
  const url = new URL(sso.authorizationEndpoint)
  const params: Record<string, string> = {
    ...sso.params,
    response_type: "code",
    client_id: sso.clientId,
    redirect_uri: attempt.redirectUri,
    scope: sso.scope,
    state: attempt.state,
    code_challenge: await codeChallenge(attempt.verifier),
    code_challenge_method: "S256",
  }
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value)
  }
  return url.toString()
}

/** Origin of this server as the browser sees it, possibly behind a reverse proxy */
export function publicOrigin(headers: Headers, requestUrl: string): string {
  const url = new URL(requestUrl)
  const host = headers.get("x-forwarded-host")?.split(",")[0]?.trim() ?? headers.get("host") ?? url.host
  const proto = headers.get("x-forwarded-proto")?.split(",")[0]?.trim() ?? url.protocol.replace(":", "")
  return `${proto}://${host}`
}
