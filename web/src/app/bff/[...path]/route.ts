import { type NextRequest } from "next/server"
import { cookies } from "next/headers"
import { needsRenewal, renewSession } from "@/lib/auth"
import { getSession, saveSession, type Session } from "@/lib/session"
import { likaFetch } from "@/lib/lika-api"
import { SESSION_COOKIE, isSecureRequest } from "@/lib/session-cookie"

/**
 * Backend for the browser: forwards /bff/api/** and /bff/public/** to the Lika API with the
 * access token of the signed-in user, which never reaches browser JavaScript.
 *
 * The access token is renewed when it expires, and when the API refuses it because the rights
 * of the user changed (token-stale). The renewed session is saved in the cookie.
 */
async function forward(request: NextRequest, { params }: RouteContext<"/bff/[...path]">) {
  const { path } = await params
  if (path[0] !== "api" && path[0] !== "public") {
    return Response.json({ message: "Not found" }, { status: 404 })
  }
  let session = await getSession()
  if (!session) {
    return Response.json({ message: "Not signed in", errorCode: "not-signed-in" }, { status: 401 })
  }

  const target = "/" + path.map(encodeURIComponent).join("/") + request.nextUrl.search
  const hasBody = request.method !== "GET" && request.method !== "HEAD"
  const body = hasBody ? await request.text() : undefined
  const send = (s: Session) =>
    likaFetch(target, s.accessToken, {
      method: request.method,
      headers: hasBody ? { "Content-Type": request.headers.get("content-type") ?? "application/json" } : undefined,
      body,
    })

  let renewed = false
  if (needsRenewal(session)) {
    session = await renewSession(session)
    renewed = true
  }
  let response = session ? await send(session) : undefined
  if (session && response?.status === 401 && !renewed) {
    // Rights changed, or the API restarted with another signing secret
    session = await renewSession(session)
    renewed = true
    response = session ? await send(session) : undefined
  }

  const store = await cookies()
  if (!session || !response) {
    store.delete(SESSION_COOKIE)
    return Response.json({ message: "Your session has ended. Sign in again.", errorCode: "session-ended" }, { status: 401 })
  }
  if (renewed) {
    await saveSession(session, isSecureRequest(request.headers, request.nextUrl.protocol))
  }
  const headers = new Headers({ "Content-Type": response.headers.get("content-type") ?? "application/json" })
  // Downloads, e.g. the Claude plugin
  const disposition = response.headers.get("content-disposition")
  if (disposition) {
    headers.set("Content-Disposition", disposition)
  }
  return new Response(response.body, { status: response.status, headers })
}

export { forward as GET, forward as POST, forward as PUT, forward as PATCH, forward as DELETE }
