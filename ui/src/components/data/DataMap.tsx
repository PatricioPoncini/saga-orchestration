import { CATEGORY_LABEL, SERVICES } from '../../data'
import type { DatabaseInfo, ServiceKey } from '../../types'

interface Props {
  databases: Partial<Record<ServiceKey, DatabaseInfo>>
  selected: { service: ServiceKey; table: string } | null
  onSelect: (service: ServiceKey, table: string) => void
}

export function DataMap({ databases, selected, onSelect }: Props) {
  return (
    <section aria-label="Data map" className="data-map">
      <div className="map-grid">
        {SERVICES.map((s) => {
          const db = databases[s.key]
          return (
            <article key={s.key} className={`db-card ${s.key === 'orchestrator' ? 'center' : ''}`}>
              <header>
                <b>{s.name}</b>
                <span className="mono">{db ? `db ${db.database}` : '…'}</span>
              </header>
              {!db && <p className="empty">No connection to the service.</p>}
              <ul>
                {db?.tables.map((t) => {
                  const active = selected?.service === s.key && selected.table === t.name
                  return (
                    <li key={t.name}>
                      <button
                        className={`table-chip cat-${t.category} ${active ? 'active' : ''}`}
                        onClick={() => onSelect(s.key, t.name)}
                        title={`${t.columns.length} columns · ${CATEGORY_LABEL[t.category]}`}
                      >
                        <span className="mono">{t.name}</span>
                        <span className="count">
                          {t.pending ? <em className="comp">{t.pending} unpublished · </em> : null}
                          {t.rowCount}
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            </article>
          )
        })}

      </div>
      <div className="legend">
        <span><i className="sq cat-domain" />business</span>
        <span><i className="sq cat-saga" />saga state</span>
        <span><i className="sq cat-messaging" />messaging (outbox / inbox)</span>
        <span className="dim">No service reads another service's database: everything goes through RabbitMQ (below). Totals include all visitors.</span>
      </div>
    </section>
  )
}
