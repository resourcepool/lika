import "server-only"
import { apiUrl } from "@/lib/env"

/**
 * Calls the Lika API from the server, on behalf of the signed-in key.
 */
export async function likaFetch(path: string, token: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`${apiUrl()}${path}`, {
    ...init,
    cache: "no-store",
    headers: {
      // The API answers errors with HTML pages unless JSON is accepted
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...init.headers,
    },
  })
}
