export interface MySaga {
  id: string
  scenarioId: string | null
  createdAt: number
}

const KEY = 'sagalab.mine'
const MAX = 20

export function mySagas(): MySaga[] {
  try {
    const raw = window.localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as MySaga[]) : []
  } catch {
    return []
  }
}

function save(list: MySaga[]) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX)))
  } catch {
    return
  }
}

export function rememberSaga(id: string, scenarioId: string | null) {
  save([{ id, scenarioId, createdAt: Date.now() }, ...mySagas().filter((s) => s.id !== id)])
}

export function keepOnly(existingIds: string[]) {
  const existing = new Set(existingIds)
  save(mySagas().filter((s) => existing.has(s.id)))
}
