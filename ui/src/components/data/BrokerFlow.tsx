import type { QueueStatus } from '../../types'

interface Route {
  key: string
  queue: string
  consumer: string
  carries: string
  returnsByTtl?: boolean
}

interface Exchange {
  name: string
  publishers: string[]
  explain: string
  routes: Route[]
}

const EXCHANGES: Exchange[] = [
  {
    name: 'saga.commands',
    publishers: ['orchestrator', 'order'],
    explain: 'Lee la routing key de cada mensaje y lo deja en la cola de ese servicio.',
    routes: [
      { key: 'orchestrator', queue: 'orchestrator.commands', consumer: 'orchestrator', carries: 'StartCreateOrderSaga' },
      { key: 'payment', queue: 'payment.commands', consumer: 'payment', carries: 'AuthorizePayment · RefundPayment' },
      { key: 'inventory', queue: 'inventory.commands', consumer: 'inventory', carries: 'ReserveStock' },
      { key: 'order', queue: 'order.commands', consumer: 'order', carries: 'ApproveOrder · RejectOrder' },
    ],
  },
  {
    name: 'saga.replies',
    publishers: ['order', 'payment', 'inventory'],
    explain: 'Todas las respuestas van a una sola cola: la del orquestador.',
    routes: [
      { key: 'orchestrator', queue: 'orchestrator.replies', consumer: 'orchestrator', carries: 'PaymentAuthorized · StockReserved · …' },
    ],
  },
  {
    name: 'saga.retry',
    publishers: ['inventory'],
    explain: 'Si inventory está caído para una saga, le devuelve sus mensajes a esta cola de espera.',
    routes: [
      { key: 'inventory', queue: 'inventory.retry', consumer: 'saga.commands', carries: 'ReserveStock en espera (TTL 2 s)', returnsByTtl: true },
    ],
  },
]

const MAX_ENVELOPES = 12

export function BrokerFlow({ queues }: { queues: QueueStatus[] }) {
  const status = (name: string) => queues.find((q) => q.name === name)
  const stuck = EXCHANGES.flatMap((e) => e.routes).filter((r) => {
    const q = status(r.queue)
    return !r.returnsByTtl && q && q.messages > 0 && q.consumers === 0
  })
  const deadLetters = status('saga.dead-letters')?.messages ?? 0

  return (
    <section className="broker-flow" aria-labelledby="broker-title">
      <div>
        <h2 className="section-title" id="broker-title">RabbitMQ: exchanges y colas</h2>
        <p className="broker-intro">
          Un <b>exchange</b> no guarda nada: recibe el mensaje y decide a qué cola mandarlo según su <b>routing key</b>.
          La <b>cola</b> sí lo guarda hasta que su servicio lo procesa. Si el servicio se cae, los mensajes esperan ahí.
        </p>
        <p className="broker-intro dim">Es el broker compartido: los contadores incluyen los mensajes de todas las personas que están usando la demo.</p>
      </div>

      {stuck.map((r) => (
        <div key={r.queue} className="callout" role="status">
          <span>
            <b>{r.consumer} no está consumiendo.</b> Hay {status(r.queue)?.messages} mensajes esperando en{' '}
            <span className="mono">{r.queue}</span>: se procesan apenas vuelva.
          </span>
        </div>
      ))}

      <div className="flow-scroll">
        <div className="flow">
          <div className="flow-head">Publican</div>
          <div className="flow-head" />
          <div className="flow-head">Exchange</div>
          <div className="flow-head">Routing key</div>
          <div className="flow-head">Cola · mensajes esperando</div>
          <div className="flow-head" />
          <div className="flow-head">Consume</div>

          {EXCHANGES.map((ex) => {
            const span = { gridRow: `span ${ex.routes.length}` }
            return ex.routes.map((r, i) => {
              const q = status(r.queue)
              const messages = q?.messages ?? 0
              const consuming = (q?.consumers ?? 0) > 0 || r.returnsByTtl === true
              const last = i === ex.routes.length - 1
              return [
                i === 0 && (
                  <div key={`${ex.name}-pub`} className="flow-cell publishers" style={span}>
                    {ex.publishers.map((p) => (
                      <span key={p} className="service-chip">{p}</span>
                    ))}
                  </div>
                ),
                i === 0 && (
                  <div key={`${ex.name}-a1`} className="flow-cell" style={span}>
                    <span className="flow-arrow" />
                  </div>
                ),
                i === 0 && (
                  <div key={`${ex.name}-ex`} className="flow-cell exchange" style={span}>
                    <span className="mono strong">{ex.name}</span>
                    <small>{ex.explain}</small>
                  </div>
                ),
                <div key={`${r.queue}-key`} className={`flow-cell ${last ? 'group-end' : ''}`}>
                  <span className="flow-arrow keyed"><em className="mono">{r.key}</em></span>
                </div>,
                <div key={`${r.queue}-q`} className={`flow-cell ${last ? 'group-end' : ''}`}>
                  <div className={`queue-tube ${messages > 0 && !consuming ? 'stuck' : ''} ${r.returnsByTtl ? 'waiting' : ''}`}>
                    <div className="queue-name">
                      <span className="mono strong">{r.queue}</span>
                      <small>{r.carries}</small>
                    </div>
                    <div className="envelopes" aria-label={`${messages} mensajes esperando`}>
                      {Array.from({ length: Math.min(messages, MAX_ENVELOPES) }).map((_, k) => (
                        <i key={k} />
                      ))}
                      {messages > MAX_ENVELOPES && <span className="more">+{messages - MAX_ENVELOPES}</span>}
                      {messages === 0 && <span className="empty-queue">vacía</span>}
                    </div>
                  </div>
                </div>,
                <div key={`${r.queue}-a2`} className={`flow-cell ${last ? 'group-end' : ''}`}>
                  <span className={`flow-arrow ${consuming ? '' : 'broken'}`} />
                </div>,
                <div key={`${r.queue}-c`} className={`flow-cell consumer ${last ? 'group-end' : ''}`}>
                  <span className="service-chip">{r.consumer}</span>
                  {r.returnsByTtl ? (
                    <small className="comp">↻ vuelven solos a los 2 s</small>
                  ) : (
                    <small className={consuming ? 'ok' : 'fail'}>{consuming ? '● consumiendo' : '○ nadie consume'}</small>
                  )}
                </div>,
              ]
            })
          })}
        </div>
      </div>

      <p className="broker-foot dim">
        Además: <span className="mono">saga.trace</span> (fanout) lleva los eventos que ves en esta web, y{' '}
        <span className="mono">saga.dead-letters</span> guarda los mensajes que fallaron 3 veces
        {deadLetters > 0 ? <> — hoy tiene <b className="fail">{deadLetters}</b></> : ' (vacía)'}.
      </p>
    </section>
  )
}
