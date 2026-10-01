/**
 * Browser-side calls to the Lika API, through the /bff route of this server.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly errorCode?: string,
  ) {
    super(message)
  }
}

type Query = Record<string, string | number | boolean | undefined | null>

export function withQuery(path: string, query?: Query): string {
  if (!query) {
    return path
  }
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, String(value))
    }
  }
  const search = params.toString()
  return search ? `${path}?${search}` : path
}

export async function api<T = void>(
  path: string,
  init: { method?: string; body?: unknown; query?: Query } = {},
): Promise<T> {
  const response = await fetch(withQuery(`/bff${path}`, init.query), {
    method: init.method ?? "GET",
    headers: init.body === undefined ? undefined : { "Content-Type": "application/json" },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })
  if (response.status === 401 && typeof window !== "undefined") {
    // The key was revoked: reload everything from the sign-in page
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign("/signin?expired=1")
  }
  const text = await response.text()
  const data = text ? safeJson(text) : undefined
  if (!response.ok) {
    const error = data as { message?: string; errorCode?: string } | undefined
    throw new ApiError(response.status, error?.message ?? `Request failed (${response.status})`, error?.errorCode)
  }
  return data as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}
