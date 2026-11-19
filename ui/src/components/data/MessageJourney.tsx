import { useState } from 'react'
import { ms, targetOf } from '../../data'
import type { OutboxMessage, ProcessedMessage, ServiceKey } from '../../types'

export interface Journey {
  message: OutboxMessage
  from: ServiceKey
  to: ServiceKey
  copies: number
  processed: ProcessedMessage | null
  retry: number
}

export function buildJourneys(
  outboxes: Partial<Record<ServiceKey, OutboxMessage[]>>,
  inboxes: Partial<Record<ServiceKey, ProcessedMessage[]>>,
): Journey[] {
  const byId = new Map<string, Journey>()
  for (const [service, messages] of Object.entries(outboxes) as [ServiceKey, OutboxMessage[]][]) {
    for (const message of messages) {
      const existing = byId.get(message.messageId)
      if (existing) {
        existing.copies++
        continue
      }
      const to = targetOf(message.exchange, message.routingKey)
      const processed = inboxes[to]?.find((p) => p.messageId === message.messageId) ?? null
      byId.set(message.messageId, { message, from: service, to, copies: 1, processed, retry: 0 })
    }
  }
  const sorted = [...byId.values()].sort((a, b) => a.message.createdAt.localeCompare(b.message.createdAt))
  const seen = new Map<string, number>()
  for (const j of sorted) {
    const key = `${j.message.type}|${j.from}|${j.to}`
    j.retry = seen.get(key) ?? 0
    seen.set(key, j.retry + 1)
  }
  return sorted
}

type Kind = 'start' | 'command' | 'ok' | 'fail' | 'comp'
const KIND: Record<string, Kind> = {
  StartCreateOrderSaga: 'start',
  AuthorizePayment: 'command',
  ReserveStock: 'command',
  ApproveOrder: 'command',
  RefundPayment: 'comp',
  RejectOrder: 'comp',
  PaymentAuthorized: 'ok',
  StockReserved: 'ok',
  OrderApproved: 'ok',
  PaymentRefunded: 'ok',
  OrderRejected: 'ok',
  PaymentRejected: 'fail',
  StockUnavailable: 'fail',
}
const KIND_LABEL: Record<Kind, string> = {
  start: 'start',
  command: 'command',
  ok: 'reply',
  fail: 'reply: failed',
  comp: 'compensation',
}

const t = (iso: string | null) => (iso ? new Date(iso).getTime() : null)
const clock = (iso: string) =>
  new Date(iso).toLocaleTimeString('en-GB', { hour12: false }) + '.' + String(new Date(iso).getMilliseconds()).padStart(3, '0')
const seconds = (n: number) => (n < 1 ? `${Math.round(n * 1000)} ms` : `${n.toFixed(n < 10 ? 1 : 0)} s`)

export function MessageJourney({ journeys }: { journeys: Journey[] }) {
  const [open, setOpen] = useState<string | null>(null)

  if (journeys.length === 0) {
    return <p className="empty">This saga doesn't have any messages yet.</p>
  }

  const now = Date.now()
  const start = Math.min(...journeys.map((j) => t(j.message.createdAt)!))
  const end = Math.max(
    ...journeys.map((j) => t(j.processed?.processedAt ?? null) ?? (j.processed ? 0 : now)),
    start + 1,
  )
  const span = end - start
  const pct = (time: number) => ((time - start) / span) * 100
  const ticks = [0, 0.25, 0.5, 0.75, 1]

  return (
    <div className="waterfall">
      <div className="wf-row wf-axis" aria-hidden="true">
        <span />
        <span className="wf-axis-label">message</span>
        <div className="wf-track">
          {ticks.map((f) => (
            <span key={f} className="wf-tick" style={{ left: `${f * 100}%` }}>
              {seconds((span * f) / 1000)}
            </span>
          ))}
        </div>
        <span className="wf-axis-label right">took</span>
      </div>

      <ol>
        {journeys.map((j, i) => {
          const created = t(j.message.createdAt)!
          const published = t(j.message.publishedAt)
          const processed = t(j.processed?.processedAt ?? null)
          const kind = KIND[j.message.type] ?? 'command'
          const total = processed ? (processed - created) / 1000 : null
          const isOpen = open === j.message.messageId

          return (
            <li key={j.message.messageId} className={`wf-item k-${kind}`}>
              <button
                className="wf-row"
                aria-expanded={isOpen}
                onClick={() => setOpen(isOpen ? null : j.message.messageId)}
              >
                <span className="wf-num mono">{String(i + 1).padStart(2, '0')}</span>
                <span className="wf-msg">
                  <span className="mono wf-type">{j.message.type}</span>
                  <span className="wf-route">
                    {j.from} <span aria-hidden="true">→</span> {j.to}
                    <span className="wf-kind">{KIND_LABEL[kind]}</span>
                    {j.retry > 0 &&
                      (kind === 'ok' || kind === 'fail' ? (
                        <span className="wf-flag comp" title="Reply to a resent command: the orchestrator already took the first one">
                          repeated (ignored)
                        </span>
                      ) : (
                        <span className="wf-flag comp" title="The orchestrator sent the command again because of a timeout">
                          resend {j.retry} after timeout
                        </span>
                      ))}
                    {j.copies > 1 && <span className="wf-flag comp">duplicate ×{j.copies}</span>}
                  </span>
                </span>
                <span className="wf-track">
                  <span
                    className={`wf-seg outbox ${published ? '' : 'pending'}`}
                    style={{ left: `${pct(created)}%`, width: `${Math.max(pct(published ?? now) - pct(created), 0.4)}%` }}
                  />
                  {published && (
                    <span
                      className={`wf-seg transit ${processed ? '' : 'pending'}`}
                      style={{ left: `${pct(published)}%`, width: `${Math.max(pct(processed ?? now) - pct(published), 0.6)}%` }}
                    />
                  )}
                  {processed && <span className="wf-dot" style={{ left: `${pct(processed)}%` }} />}
                </span>
                <span className="wf-total">
                  {total !== null ? (
                    <span className="mono">{seconds(total)}</span>
                  ) : (
                    <span className="comp">{published ? 'in the queue' : 'in the outbox'}</span>
                  )}
                </span>
              </button>

              {isOpen && (
                <dl className="wf-detail">
                  <div><dt>1 · outbox of {j.from}</dt><dd className="mono">{clock(j.message.createdAt)}</dd></div>
                  <div>
                    <dt>2 · published to RabbitMQ</dt>
                    <dd className="mono">
                      {j.message.publishedAt ? `+${ms(j.message.createdAt, j.message.publishedAt)}` : 'not yet'} ·{' '}
                      {j.message.exchange} → {j.message.routingKey}
                    </dd>
                  </div>
                  <div>
                    <dt>3 · processed by {j.to}</dt>
                    <dd className="mono">
                      {j.processed ? `+${ms(j.message.publishedAt, j.processed.processedAt)} after publishing` : 'not yet'}
                    </dd>
                  </div>
                  <div><dt>message id</dt><dd className="mono">{j.message.messageId}</dd></div>
                </dl>
              )}
            </li>
          )
        })}
      </ol>
    </div>
  )
}
