import { NextResponse, type NextRequest } from "next/server"
import { SESSION_COOKIE, SESSION_MAX_AGE, isSecureRequest } from "@/lib/session-cookie"

/**
 * Sends visitors without a session to the sign-in page, and slides the
 * session expiry forward on every visit. The session itself is decrypted and
 * checked by the pages and the /bff route.
 */
export function proxy(request: NextRequest) {
  const session = request.cookies.get(SESSION_COOKIE)?.value
  const { pathname } = request.nextUrl
  const isSignIn = pathname === "/signin"

  if (!session) {
    if (isSignIn || pathname.startsWith("/bff/")) {
      return NextResponse.next()
    }
    return NextResponse.redirect(new URL("/signin", request.url))
  }

  const response = NextResponse.next()
  response.cookies.set(SESSION_COOKIE, session, {
    httpOnly: true,
    sameSite: "lax",
    secure: isSecureRequest(request.headers, request.nextUrl.protocol),
    path: "/",
    maxAge: SESSION_MAX_AGE,
  })
  return response
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|health|favicon.ico|.*\\.(?:svg|png|ico|webp)$).*)"],
}
