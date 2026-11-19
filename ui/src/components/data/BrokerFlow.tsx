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
    explain: 'Reads the routing key of each message and puts it in that service\'s queue.',
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
    explain: 'All replies go to a single queue: the orchestrator\'s.',
    routes: [
      { key: 'orchestrator', queue: 'orchestrator.replies', consumer: 'orchestrator', carries: 'PaymentAuthorized · StockReserved · …' },
    ],
  },
  {
    name: 'saga.retry',
    publishers: ['inventory'],
    explain: 'If inventory is down for a saga, it sends that saga\'s messages back to this waiting queue.',
    routes: [
      { key: 'inventory', queue: 'inventory.retry', consumer: 'saga.commands', carries: 'ReserveStock waiting (TTL 2 s)', returnsByTtl: true },
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
        <h2 className="section-title" id="broker-title">RabbitMQ: exchanges and queues</h2>
        <p className="broker-intro">
          An <b>exchange</b> doesn't store anything: it receives the message and decides which queue to send it to based on its <b>routing key</b>.
          The <b>queue</b> does store it until its service processes it. If the service goes down, the messages wait there.
        </p>
        <p className="broker-intro dim">The counters include the messages of all running sagas, not only the one you're looking at.</p>
      </div>

      {stuck.map((r) => (
        <div key={r.queue} className="callout" role="status">
          <span>
            <b>{r.consumer} is not consuming.</b> There are {status(r.queue)?.messages} messages waiting in{' '}
            <span className="mono">{r.queue}</span>: they'll be processed as soon as it comes back.
          </span>
        </div>
      ))}

      <div className="flow-scroll">
        <div className="flow">
          <div className="flow-head">Publishers</div>
          <div className="flow-head" />
          <div className="flow-head">Exchange</div>
          <div className="flow-head">Routing key</div>
          <div className="flow-head">Queue · messages waiting</div>
          <div className="flow-head" />
          <div className="flow-head">Consumer</div>

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
                    <div className="envelopes" aria-label={`${messages} messages waiting`}>
                      {Array.from({ length: Math.min(messages, MAX_ENVELOPES) }).map((_, k) => (
                        <i key={k} />
                      ))}
                      {messages > MAX_ENVELOPES && <span className="more">+{messages - MAX_ENVELOPES}</span>}
                      {messages === 0 && <span className="empty-queue">empty</span>}
                    </div>
                  </div>
                </div>,
                <div key={`${r.queue}-a2`} className={`flow-cell ${last ? 'group-end' : ''}`}>
                  <span className={`flow-arrow ${consuming ? '' : 'broken'}`} />
                </div>,
                <div key={`${r.queue}-c`} className={`flow-cell consumer ${last ? 'group-end' : ''}`}>
                  <span className="service-chip">{r.consumer}</span>
                  {r.returnsByTtl ? (
                    <small className="comp">↻ they come back on their own after 2 s</small>
                  ) : (
                    <small className={consuming ? 'ok' : 'fail'}>{consuming ? '● consuming' : '○ no consumer'}</small>
                  )}
                </div>,
              ]
            })
          })}
        </div>
      </div>

      <p className="broker-foot dim">
        Also: <span className="mono">saga.trace</span> (fanout) carries the events you see on this site, and{' '}
        <span className="mono">saga.dead-letters</span> keeps the messages that failed 3 times
        {deadLetters > 0 ? <> — right now it has <b className="fail">{deadLetters}</b></> : ' (empty)'}.
      </p>
    </section>
  )
}
