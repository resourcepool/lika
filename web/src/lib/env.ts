import "server-only"

/**
 * Runtime configuration, read when the server starts (not at build time),
 * so a single image works for every deployment.
 */
export function apiUrl(): string {
  // API_BASE_URL is the variable of the former Angular image
  const url = process.env.LIKA_API_URL ?? process.env.API_BASE_URL
  if (!url) {
    throw new Error("LIKA_API_URL is not set: point it to the Lika API, e.g. http://lika-api:8080")
  }
  return url.replace(/\/+$/, "")
}

export function sessionSecret(): string {
  const secret = process.env.LIKA_SESSION_SECRET
  if (!secret || secret.length < 32) {
    throw new Error("LIKA_SESSION_SECRET must be set to a random string of at least 32 characters, e.g. `openssl rand -base64 48`")
  }
  return secret
}
