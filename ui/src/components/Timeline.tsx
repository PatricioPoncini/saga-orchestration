import { useState } from 'react'
import type { Tone } from '../labels'
import type { TimelineEntry } from '../types'

interface Kind {
  label: string
  tone: Tone
  technical?: boolean
}

function classify(e: TimelineEntry): Kind {
  switch (e.kind) {
    case 'COMMAND_SENT':
      return e.description.includes('compensación') ? { label: 'compensación', tone: 'comp' } : { label: 'comando', tone: '' }
    case 'REPLY_RECEIVED':
      return e.description.includes('FALLÓ') ? { label: 'falló', tone: 'fail' } : { label: 'respuesta', tone: 'ok' }
    case 'REPLY_IGNORED':
      return { label: 'descartada', tone: 'comp' }
    case 'SAGA_NOTE':
      return { label: 'decisión', tone: '' }
    case 'SAGA_FINISHED':
      return { label: 'fin', tone: 'strong' }
    case 'LOCAL_TX_COMMITTED':
      return { label: 'tx local', tone: '' }
    case 'DUPLICATE_IGNORED':
      return { label: 'duplicado', tone: 'comp' }
    case 'CHAOS':
      return { label: 'falla inyectada', tone: 'fail' }
    case 'MESSAGE_PUBLISHED':
      return { label: 'outbox → broker', tone: 'dim', technical: true }
    case 'MESSAGE_RECEIVED':
      return { label: 'recibido', tone: 'dim', technical: true }
    default:
      return { label: e.kind.toLowerCase(), tone: '' }
  }
}

function detail(e: TimelineEntry): string {
  if (e.kind === 'COMMAND_SENT') return e.description.match(/\(intento \d+\)/)?.[0] ?? ''
  if (e.kind === 'REPLY_RECEIVED') return e.description.match(/FALLÓ: (.*)\)$/)?.[1] ?? ''
  return e.description
}

export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  const [technical, setTechnical] = useState(false)
  const times = entries.map((e) => new Date(e.occurredAt).getTime())
  const start = times.length ? Math.min(...times) : 0
  const visible = entries.filter((e) => technical || !classify(e).technical)

  return (
    <section aria-label="Línea de tiempo">
      <div className="timeline-head">
        <h2 className="section-title">Línea de tiempo</h2>
        <label className="check">
          <input type="checkbox" checked={technical} onChange={(e) => setTechnical(e.target.checked)} />
          detalle de outbox / recepción
        </label>
      </div>
      {visible.length === 0 && <p className="empty">Esperando los primeros eventos…</p>}
      <ol className="events">
        {visible.map((e) => {
          const kind = classify(e)
          const offset = (new Date(e.occurredAt).getTime() - start) / 1000
          const who = e.target ? `${e.actor} → ${e.target}` : e.actor
          const whoTone = kind.tone === 'comp' || kind.tone === 'fail' ? kind.tone : ''
          const extra = detail(e)
          return (
            <li key={`${e.id}-${e.kind}`} className={`event ${kind.technical ? 'technical' : ''}`}>
              <span className="t">+{offset.toFixed(2)}s</span>
              <span className={`who ${whoTone}`}>{who}</span>
              <span className="what">
                <span className={`pill ${kind.tone}`}>{kind.label}</span>
                {e.messageType && <span className="msg">{e.messageType}</span>}
                {extra && <span className="desc">{extra}</span>}
              </span>
            </li>
          )
        })}
      </ol>
    </section>
  )
}
