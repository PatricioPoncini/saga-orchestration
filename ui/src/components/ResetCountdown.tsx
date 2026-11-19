import { useEffect, useState } from 'react'

interface ResetStatus {
  enabled: boolean
  intervalSeconds: number
  nextResetAt: string | null
}

const fmt = (seconds: number) => {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

export function ResetCountdown({ onReset }: { onReset: () => void }) {
  const [status, setStatus] = useState<ResetStatus | null>(null)
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    const load = () =>
      fetch('/api/orchestrator/inspect/reset')
        .then((r) => (r.ok ? r.json() : null))
        .then(setStatus)
        .catch(() => undefined)
    load()
    const refresh = window.setInterval(load, 60_000)
    const clock = window.setInterval(() => setNow(Date.now()), 1000)
    return () => {
      window.clearInterval(refresh)
      window.clearInterval(clock)
    }
  }, [])

  const next = status?.nextResetAt ? new Date(status.nextResetAt).getTime() : null
  const remaining = next ? Math.max(0, Math.round((next - now) / 1000)) : null

  useEffect(() => {
    if (remaining !== 0) return
    const t = window.setTimeout(() => {
      onReset()
      fetch('/api/orchestrator/inspect/reset').then((r) => r.json()).then(setStatus).catch(() => undefined)
    }, 2500)
    return () => window.clearTimeout(t)
  }, [remaining, onReset])

  if (!status?.enabled || remaining === null) return null
  const minutes = Math.round(status.intervalSeconds / 60)
  return (
    <span
      className={`reset ${remaining < 60 ? 'soon' : ''}`}
      title={`This is a demo: every ${minutes} min all databases are emptied (DEMO_RESET_INTERVAL).`}
    >
      {remaining === 0 ? 'resetting…' : <>resets in <span className="mono">{fmt(remaining)}</span></>}
    </span>
  )
}
