export const SESSION_COOKIE = "lika_session"

/**
 * Browsers cap cookie lifetime around 400 days. The proxy pushes the expiry
 * back on every visit, so a session only ends on sign-out or key revocation.
 */
export const SESSION_MAX_AGE = 400 * 24 * 60 * 60

/**
 * Whether the browser reached us over HTTPS, possibly through a TLS-terminating reverse proxy.
 * @param protocol protocol of the request URL as seen by this server, if known
 */
export function isSecureRequest(headers: Headers, protocol?: string): boolean {
  const forwarded = headers.get("x-forwarded-proto")?.split(",")[0]?.trim()
  if (forwarded) {
    return forwarded === "https"
  }
  const origin = headers.get("origin")
  if (origin) {
    return origin.startsWith("https:")
  }
  return protocol === "https:"
}
