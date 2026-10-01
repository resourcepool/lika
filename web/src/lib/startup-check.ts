import { apiUrl, sessionSecret } from "@/lib/env"

export function checkConfiguration() {
  try {
    sessionSecret()
    console.log(`Lika API: ${apiUrl()}`)
  } catch (error) {
    // Next.js would log this and keep serving errors: stop instead
    console.error(error instanceof Error ? error.message : error)
    process.exit(1)
  }
  if (!process.env.LIKA_API_URL) {
    console.warn("API_BASE_URL is deprecated, use LIKA_API_URL")
  }
}
