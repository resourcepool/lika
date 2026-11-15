import "server-only"
import { apiUrl } from "@/lib/env"

/**
 * Calls the Lika API from the server, on behalf of the signed-in user when a token is given.
 */
export async function likaFetch(path: string, token?: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`${apiUrl()}${path}`, {
    ...init,
    cache: "no-store",
    headers: {
      // The API answers errors with HTML pages unless JSON is accepted
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })
}

export type ApiFailure = { status: number; message: string; errorCode?: string }

/** JSON call to the API; failures carry the API message */
export async function likaJson<T>(path: string, init: { method?: string; body?: unknown; token?: string } = {}): Promise<{ ok: true; data: T } | ({ ok: false } & ApiFailure)> {
  const response = await likaFetch(path, init.token, {
    method: init.method ?? (init.body === undefined ? "GET" : "POST"),
    headers: init.body === undefined ? undefined : { "Content-Type": "application/json" },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })
  const text = await response.text()
  let data: unknown
  try {
    data = text ? JSON.parse(text) : undefined
  } catch {
    data = undefined
  }
  if (!response.ok) {
    const error = data as { message?: string; errorCode?: string } | undefined
    return { ok: false, status: response.status, message: error?.message ?? `The API answered ${response.status}`, errorCode: error?.errorCode }
  }
  return { ok: true, data: data as T }
}
