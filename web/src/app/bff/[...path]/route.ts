import { type NextRequest } from "next/server"
import { cookies } from "next/headers"
import { getSession } from "@/lib/session"
import { likaFetch } from "@/lib/lika-api"
import { SESSION_COOKIE } from "@/lib/session-cookie"

/**
 * Backend for the browser: forwards /bff/api/** and /bff/public/** to the Lika API
 * with the signed-in key, which never reaches browser JavaScript.
 */
async function forward(request: NextRequest, { params }: RouteContext<"/bff/[...path]">) {
  const { path } = await params
  if (path[0] !== "api" && path[0] !== "public") {
    return Response.json({ message: "Not found" }, { status: 404 })
  }
  const session = await getSession()
  if (!session) {
    return Response.json({ message: "Not signed in" }, { status: 401 })
  }

  const target = "/" + path.map(encodeURIComponent).join("/") + request.nextUrl.search
  const hasBody = request.method !== "GET" && request.method !== "HEAD"
  const response = await likaFetch(target, session.token, {
    method: request.method,
    headers: hasBody ? { "Content-Type": request.headers.get("content-type") ?? "application/json" } : undefined,
    body: hasBody ? await request.text() : undefined,
  })

  if (response.status === 401) {
    // The key was regenerated or deleted: the session is over
    ;(await cookies()).delete(SESSION_COOKIE)
  }
  return new Response(response.body, {
    status: response.status,
    headers: { "Content-Type": response.headers.get("content-type") ?? "application/json" },
  })
}

export { forward as GET, forward as POST, forward as PUT, forward as PATCH, forward as DELETE }
