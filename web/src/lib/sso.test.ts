import { describe, expect, it } from "vitest"
import { authorizationUrl, codeChallenge, publicOrigin, randomToken } from "@/lib/sso"

const sso = { label: "Keycloak", authorizationEndpoint: "https://sso.test/realms/lika/protocol/openid-connect/auth", clientId: "lika", scope: "openid email", params: { prompt: "login" } }

describe("single sign-on", () => {
  it("derives the S256 challenge of RFC 7636", async () => {
    // Example of RFC 7636, appendix B
    expect(await codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).toBe("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
  })

  it("builds the authorization request with PKCE and state", async () => {
    const url = new URL(await authorizationUrl(sso, { state: "s1", verifier: "v".repeat(43), redirectUri: "https://lika.test/auth/callback", next: "/links" }))
    expect(url.origin + url.pathname).toBe(sso.authorizationEndpoint)
    expect(Object.fromEntries(url.searchParams)).toMatchObject({
      response_type: "code",
      client_id: "lika",
      redirect_uri: "https://lika.test/auth/callback",
      scope: "openid email",
      state: "s1",
      code_challenge_method: "S256",
      prompt: "login",
    })
  })

  it("generates url-safe random tokens", () => {
    expect(randomToken()).toMatch(/^[A-Za-z0-9_-]{43}$/)
    expect(randomToken()).not.toBe(randomToken())
  })

  it("uses the origin of the browser behind a reverse proxy", () => {
    expect(publicOrigin(new Headers({ "x-forwarded-host": "lika.example.com", "x-forwarded-proto": "https" }), "http://web:80/auth/sso")).toBe("https://lika.example.com")
    expect(publicOrigin(new Headers({ host: "localhost:3001" }), "http://localhost:3001/auth/sso")).toBe("http://localhost:3001")
  })
})
