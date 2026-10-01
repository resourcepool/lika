"use client"

import { createContext, useContext } from "react"

export type ClientSession = {
  admin: boolean
  accountId?: string
  accountName?: string
}

const SessionContext = createContext<ClientSession>({ admin: false })

export function SessionProvider({ value, children }: { value: ClientSession; children: React.ReactNode }) {
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

/** What the signed-in key may do. The API enforces it; this only shapes the UI. */
export function useSession(): ClientSession {
  return useContext(SessionContext)
}
