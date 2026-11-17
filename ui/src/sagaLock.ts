import { useCallback, useEffect, useState } from 'react'
import type { SagaStatus, SagaView } from './types'

interface Lock {
  sagaId: string
  scenarioId: string | null
  startedAt: number
  seen: boolean
}

const KEY = 'sagalab.lock'
const TERMINAL: SagaStatus[] = ['COMPLETED', 'COMPENSATED', 'FAILED']
const NEVER_STARTED_MS = 30_000

export function readLock(): Lock | null {
  try {
    const raw = window.sessionStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as Lock) : null
  } catch {
    return null
  }
}

function writeLock(lock: Lock | null) {
  try {
    if (lock) window.sessionStorage.setItem(KEY, JSON.stringify(lock))
    else window.sessionStorage.removeItem(KEY)
  } catch {
    return
  }
}

export function useSagaLock(saga: SagaView | null, missing: boolean) {
  const [lock, setLockState] = useState<Lock | null>(readLock)

  const set = useCallback((next: Lock | null) => {
    setLockState(next)
    writeLock(next)
  }, [])

  useEffect(() => {
    if (!lock) return
    if (saga && saga.id === lock.sagaId) {
      if (TERMINAL.includes(saga.status)) set(null)
      else if (!lock.seen) set({ ...lock, seen: true })
    } else if (missing && (lock.seen || Date.now() - lock.startedAt > NEVER_STARTED_MS)) {
      set(null)
    }
  }, [saga, missing, lock, set])

  useEffect(() => {
    if (!lock || lock.seen) return
    const t = window.setTimeout(() => setLockState((l) => (l ? { ...l } : l)), NEVER_STARTED_MS + 500)
    return () => window.clearTimeout(t)
  }, [lock])

  return {
    lockedSagaId: lock?.sagaId ?? null,
    lock: useCallback(
      (sagaId: string, scenarioId: string | null) => set({ sagaId, scenarioId, startedAt: Date.now(), seen: false }),
      [set],
    ),
  }
}
