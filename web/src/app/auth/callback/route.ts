import { NextResponse, type NextRequest } from "next/server"
import { likaJson } from "@/lib/lika-api"
import { decryptPayload, encryptSession, sessionFromTokens } from "@/lib/session"
import { SESSION_COOKIE, SESSION_MAX_AGE, isSecureRequest } from "@/lib/session-cookie"
import { SSO_COOKIE, type SsoAttempt } from "@/lib/sso"
import type { AuthTokens } from "@/lib/types"

/**
 * End of single sign-on: checks the state, has the API exchange the code, and opens the session.
 */
export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams
  const attempt = (await decryptPayload(request.cookies.get(SSO_COOKIE)?.value)) as SsoAttempt | null
  const fail = (error: string) => {
    const response = NextResponse.redirect(new URL(`/signin?error=${encodeURIComponent(error)}`, request.url))
    response.cookies.delete({ name: SSO_COOKIE, path: "/auth" })
    return response
  }

  if (params.get("error")) {
    // e.g. access_denied when the user cancels
    return fail(params.get("error") === "access_denied" ? "sso-cancelled" : "sso-failed")
  }
  const code = params.get("code")
  if (!attempt || !code || params.get("state") !== attempt.state) {
    return fail("sso-expired")
  }

  let result
  try {
    result = await likaJson<AuthTokens>("/api/auth/sso/token", { body: { code, codeVerifier: attempt.verifier, redirectUri: attempt.redirectUri } })
  } catch (e) {
    console.error("Single sign-on failed", e)
    return fail("api-unreachable")
  }
  if (!result.ok) {
    return fail(result.errorCode ?? "sso-failed")
  }

  const response = NextResponse.redirect(new URL(attempt.next, request.url))
  response.cookies.delete({ name: SSO_COOKIE, path: "/auth" })
  response.cookies.set(SESSION_COOKIE, await encryptSession(sessionFromTokens(result.data)), {
    httpOnly: true,
    sameSite: "lax",
    secure: isSecureRequest(request.headers, request.nextUrl.protocol),
    path: "/",
    maxAge: SESSION_MAX_AGE,
  })
  return response
}
