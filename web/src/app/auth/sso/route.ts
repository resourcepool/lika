import { NextResponse, type NextRequest } from "next/server"
import { authConfig } from "@/lib/auth"
import { encryptPayload } from "@/lib/session"
import { isSecureRequest } from "@/lib/session-cookie"
import { CALLBACK_PATH, SSO_COOKIE, SSO_COOKIE_MAX_AGE, authorizationUrl, publicOrigin, randomToken, type SsoAttempt } from "@/lib/sso"

/** Same-site path only, to avoid open redirects */
function safeNext(value: string | null): string {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "/links"
}

/**
 * Starts single sign-on: remembers a PKCE verifier and a state in an encrypted cookie,
 * then sends the browser to the identity provider.
 */
export async function GET(request: NextRequest) {
  const config = await authConfig()
  if (!config?.sso) {
    return NextResponse.redirect(new URL("/signin?error=sso-unavailable", request.url))
  }
  const attempt: SsoAttempt = {
    state: randomToken(),
    verifier: randomToken(48),
    redirectUri: publicOrigin(request.headers, request.url) + CALLBACK_PATH,
    next: safeNext(request.nextUrl.searchParams.get("next")),
  }
  const response = NextResponse.redirect(await authorizationUrl(config.sso, attempt))
  response.cookies.set(SSO_COOKIE, await encryptPayload(attempt), {
    httpOnly: true,
    // Lax: the provider sends the browser back with a top-level GET
    sameSite: "lax",
    secure: isSecureRequest(request.headers, request.nextUrl.protocol),
    path: "/auth",
    maxAge: SSO_COOKIE_MAX_AGE,
  })
  return response
}
