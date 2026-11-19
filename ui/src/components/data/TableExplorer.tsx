import { CATEGORY_LABEL, SERVICES, TABLE_INFO } from '../../data'
import type { DatabaseInfo, ServiceKey, TableRows } from '../../types'

interface Props {
  databases: Partial<Record<ServiceKey, DatabaseInfo>>
  selected: { service: ServiceKey; table: string } | null
  onSelect: (service: ServiceKey, table: string) => void
  rows: TableRows | null
  onlySaga: boolean
  onOnlySaga: (v: boolean) => void
  sagaId: string | null
  orderId: string | null
}

const STATUS_TONE: Record<string, string> = {
  APPROVED: 'ok', AUTHORIZED: 'ok', RESERVED: 'ok', COMPLETED: 'ok', SUCCEEDED: 'ok',
  REFUNDED: 'comp', COMPENSATED: 'comp', COMPENSATING: 'comp',
  REJECTED: 'fail', FAILED: 'fail',
  PENDING: 'strong', RUNNING: 'strong',
}

export function TableExplorer({ databases, selected, onSelect, rows, onlySaga, onOnlySaga, sagaId, orderId }: Props) {
  const db = selected ? databases[selected.service] : undefined
  const table = db?.tables.find((t) => t.name === selected?.table)
  const hasSagaColumn = table?.columns.some((c) => c.name === 'saga_id' || c.name === 'order_id') ?? false

  const related = (row: Record<string, unknown>) =>
    (sagaId && row.saga_id === sagaId) || (orderId && (row.order_id === orderId || row.id === orderId))

  return (
    <section id="explorer" aria-label="Table explorer" className="explorer">
      <div className="explorer-tabs" role="tablist" aria-label="Service">
        {SERVICES.map((s) => (
          <button
            key={s.key}
            role="tab"
            aria-selected={selected?.service === s.key}
            className={`tab ${selected?.service === s.key ? 'active' : ''}`}
            onClick={() => {
              const first = databases[s.key]?.tables[0]?.name
              if (first) onSelect(s.key, first)
            }}
          >
            {s.name}
          </button>
        ))}
      </div>

      {db && (
        <div className="explorer-tables">
          {db.tables.map((t) => (
            <button
              key={t.name}
              className={`table-chip inline cat-${t.category} ${t.name === selected?.table ? 'active' : ''}`}
              onClick={() => onSelect(selected!.service, t.name)}
            >
              <span className="mono">{t.name}</span>
              <span className="count">{t.rowCount}</span>
            </button>
          ))}
        </div>
      )}

      {table && (
        <div className="explorer-head">
          <div>
            <p className="mono strong" style={{ margin: 0, fontSize: 18 }}>
              {db?.database}.{table.name} <span className={`pill cat-pill-${table.category}`}>{CATEGORY_LABEL[table.category]}</span>
            </p>
            <p style={{ margin: '4px 0 0' }}>{TABLE_INFO[table.name] ?? ''}</p>
          </div>
          <label className={`check ${hasSagaColumn ? '' : 'disabled'}`} title={hasSagaColumn ? '' : 'This table has no saga_id or order_id: it shows everyone\'s rows'}>
            <input type="checkbox" checked={onlySaga && hasSagaColumn} disabled={!hasSagaColumn || !sagaId} onChange={(e) => onOnlySaga(e.target.checked)} />
            only rows from your saga
          </label>
        </div>
      )}

      {rows && table && (
        <div className="grid-wrap">
          <table className="data-grid">
            <thead>
              <tr>
                {rows.columns.map((c) => (
                  <th key={c.name} scope="col">
                    <span className="mono">{c.name}</span>
                    {c.primaryKey && <span className="pk">PK</span>}
                    <small>{c.type}</small>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.rows.length === 0 && (
                <tr><td colSpan={rows.columns.length} className="empty">No rows.</td></tr>
              )}
              {rows.rows.map((row, i) => (
                <tr key={i} className={related(row) ? 'related' : ''}>
                  {rows.columns.map((c) => {
                    const value = row[c.name]
                    const text = value === null || value === undefined ? '—' : String(value)
                    const tone = typeof value === 'string' ? STATUS_TONE[value] ?? '' : ''
                    return (
                      <td key={c.name} title={text} className={`${tone} ${value === null ? 'dim' : ''}`}>
                        {text}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
          <p className="grid-foot dim">
            {rows.rows.length} of {rows.total} rows{rows.filteredBySaga ? ' (filtered by saga)' : ''} · newest first ·
            highlighted: related to the selected saga
          </p>
        </div>
      )}
    </section>
  )
}
