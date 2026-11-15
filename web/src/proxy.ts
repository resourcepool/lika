import { NextResponse, type NextRequest } from "next/server"
import { SESSION_COOKIE, SESSION_MAX_AGE, isSecureRequest } from "@/lib/session-cookie"

/** Pages reachable without a session: signing in, and the links sent by email */
const PUBLIC_PATHS = ["/signin", "/setup", "/forgot-password"]
const PUBLIC_PREFIXES = ["/invite/", "/reset-password/", "/bff/", "/auth/"]

function isPublicPath(pathname: string): boolean {
  return PUBLIC_PATHS.includes(pathname) || PUBLIC_PREFIXES.some((p) => pathname.startsWith(p))
}

/**
 * Sends visitors without a session to the sign-in page, and slides the
 * session expiry forward on every visit. The session itself is decrypted and
 * checked by the pages and the /bff route, which renews its tokens.
 */
export function proxy(request: NextRequest) {
  const session = request.cookies.get(SESSION_COOKIE)?.value
  const { pathname, search } = request.nextUrl

  if (!session) {
    if (isPublicPath(pathname)) {
      return NextResponse.next()
    }
    const signIn = new URL("/signin", request.url)
    if (pathname !== "/") {
      signIn.searchParams.set("next", pathname + search)
    }
    return NextResponse.redirect(signIn)
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
