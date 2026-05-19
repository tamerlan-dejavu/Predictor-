import { useMemo, useState } from 'react'

function abbreviateName(name) {
  // Check longer/more specific patterns first
  if (name.includes('Tournament')) return 'Tournament'
  if (name.includes('GShare')) return 'GShare'
  if (name.includes('Bimodal')) return 'Bimodal'
  if (name.includes('ALWAYS_TAKEN')) return 'Static_T'
  if (name.includes('ALWAYS_NOT_TAKEN')) return 'Static_NT'
  if (name.includes('BTFN')) return 'Static_BTFN'
  return name
}

const COLUMNS = [
  { key: 'predictorName',     label: 'Predictor',     align: 'left'  },
  { key: 'totalPredictions',  label: 'Total',         align: 'right' },
  { key: 'mispredictions',    label: 'Misses',        align: 'right' },
  { key: 'mispredictionRate', label: 'Rate %',        align: 'right' },
  { key: 'mpki',              label: 'MPKI',          align: 'right' },
]

export default function ResultsTable({ rows }) {
  const [sortKey, setSortKey] = useState('mispredictionRate')
  const [sortDir, setSortDir] = useState('asc')

  const { sorted, minRate, maxRate } = useMemo(() => {
    if (!rows || rows.length === 0) return { sorted: [], minRate: null, maxRate: null }
    const sorted = [...rows].sort((a, b) => {
      const av = a[sortKey]
      const bv = b[sortKey]
      const cmp = typeof av === 'string' ? av.localeCompare(bv) : av - bv
      return sortDir === 'asc' ? cmp : -cmp
    })
    const rates = rows.map((r) => r.mispredictionRate)
    return { sorted, minRate: Math.min(...rates), maxRate: Math.max(...rates) }
  }, [rows, sortKey, sortDir])

  if (!rows || rows.length === 0) return null

  const handleSort = (key) => {
    if (key === sortKey) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDir('asc')
    }
  }

  const arrow = (key) => {
    if (key !== sortKey) return <span className="text-gray-600 ml-1">↕</span>
    return <span className="text-blue-400 ml-1">{sortDir === 'asc' ? '↑' : '↓'}</span>
  }

  const rowClass = (rate) => {
    if (minRate !== null && rate === minRate && minRate !== maxRate) {
      return 'bg-green-900/40 hover:bg-green-900/60 border-l-2 border-green-500'
    }
    if (maxRate !== null && rate === maxRate && minRate !== maxRate) {
      return 'bg-red-900/30 hover:bg-red-900/50 border-l-2 border-red-500'
    }
    return 'hover:bg-gray-700/50'
  }

  return (
    <div className="bg-gray-800 rounded-lg border border-gray-700 overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-gray-900 text-gray-300">
          <tr>
            {COLUMNS.map((col) => (
              <th
                key={col.key}
                onClick={() => handleSort(col.key)}
                className={`px-4 py-2.5 font-medium cursor-pointer select-none hover:text-white ${
                  col.align === 'right' ? 'text-right' : 'text-left'
                }`}
              >
                {col.label}{arrow(col.key)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sorted.map((r, i) => (
            <tr
              key={`${r.predictorName}-${i}`}
              className={`border-t border-gray-700 transition-colors ${rowClass(r.mispredictionRate)}`}
              title={r.predictorName}
            >
              <td className="px-4 py-2.5 font-medium text-white">{abbreviateName(r.predictorName)}</td>
              <td className="px-4 py-2.5 text-right tabular-nums text-gray-200">{r.totalPredictions.toLocaleString()}</td>
              <td className="px-4 py-2.5 text-right tabular-nums text-gray-200">{r.mispredictions.toLocaleString()}</td>
              <td className="px-4 py-2.5 text-right tabular-nums text-gray-200">{r.mispredictionRate.toFixed(2)}%</td>
              <td className="px-4 py-2.5 text-right tabular-nums text-gray-200">{r.mpki.toFixed(3)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
