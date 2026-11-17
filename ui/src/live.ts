import { useEffect, useState } from 'react'

export function useSagaStream(sagaId: string | null, onLive: (live: boolean) => void): number {
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let timer: number | undefined
    const url = sagaId ? `/api/orchestrator/stream?sagaId=${encodeURIComponent(sagaId)}` : '/api/orchestrator/stream'
    const source = new EventSource(url)
    source.addEventListener('hello', () => onLive(true))
    source.addEventListener('timeline', () => {
      window.clearTimeout(timer)
      timer = window.setTimeout(() => setTick((t) => t + 1), 150)
    })
    source.onerror = () => onLive(false)
    return () => {
      source.close()
      window.clearTimeout(timer)
    }
  }, [sagaId, onLive])

  return tick
}
