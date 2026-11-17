import { CATEGORY_LABEL, SERVICES } from '../../data'
import type { DatabaseInfo, ServiceKey } from '../../types'

interface Props {
  databases: Partial<Record<ServiceKey, DatabaseInfo>>
  selected: { service: ServiceKey; table: string } | null
  onSelect: (service: ServiceKey, table: string) => void
}

export function DataMap({ databases, selected, onSelect }: Props) {
  return (
    <section aria-label="Mapa de datos" className="data-map">
      <div className="map-grid">
        {SERVICES.map((s) => {
          const db = databases[s.key]
          return (
            <article key={s.key} className={`db-card ${s.key === 'orchestrator' ? 'center' : ''}`}>
              <header>
                <b>{s.name}</b>
                <span className="mono">{db ? `db ${db.database}` : '…'}</span>
              </header>
              {!db && <p className="empty">Sin conexión con el servicio.</p>}
              <ul>
                {db?.tables.map((t) => {
                  const active = selected?.service === s.key && selected.table === t.name
                  return (
                    <li key={t.name}>
                      <button
                        className={`table-chip cat-${t.category} ${active ? 'active' : ''}`}
                        onClick={() => onSelect(s.key, t.name)}
                        title={`${t.columns.length} columnas · ${CATEGORY_LABEL[t.category]}`}
                      >
                        <span className="mono">{t.name}</span>
                        <span className="count">
                          {t.pending ? <em className="comp">{t.pending} sin publicar · </em> : null}
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
        <span><i className="sq cat-domain" />negocio</span>
        <span><i className="sq cat-saga" />estado de la saga</span>
        <span><i className="sq cat-messaging" />mensajería (outbox / inbox)</span>
        <span className="dim">Ningún servicio lee la base de otro: todo cruza por RabbitMQ (abajo). Los totales son de todos los visitantes.</span>
      </div>
    </section>
  )
}
