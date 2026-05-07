import { useState } from 'react'

const PREDICTORS = [
  { value: 'static_taken', label: 'Static (always taken)' },
  { value: 'bimodal', label: 'Bimodal' },
  { value: 'gshare', label: 'GShare' },
  { value: 'tournament', label: 'Tournament' },
]

const SAMPLE_TRACE = `0x400000 1
0x400000 1
0x400000 1
0x400000 0
0x400010 1
0x400010 0
`

export default function PredictorForm({ onSubmit, mode = 'single', loading = false }) {
  const [predictorType, setPredictorType] = useState('gshare')
  const [selected, setSelected] = useState(['bimodal', 'gshare', 'tournament'])
  const [tableSize, setTableSize] = useState(1024)
  const [historyBits, setHistoryBits] = useState(8)
  const [traceContent, setTraceContent] = useState(SAMPLE_TRACE)

  const toggle = (val) =>
    setSelected((prev) =>
      prev.includes(val) ? prev.filter((p) => p !== val) : [...prev, val]
    )

  const handleSubmit = (e) => {
    e.preventDefault()
    if (mode === 'compare') {
      onSubmit({ predictors: selected, tableSize: Number(tableSize), historyBits: Number(historyBits), traceContent })
    } else {
      onSubmit({ predictorType, tableSize: Number(tableSize), historyBits: Number(historyBits), traceContent })
    }
  }

  return (
    <form onSubmit={handleSubmit} className="bg-white rounded-lg border border-slate-200 p-6 space-y-4">
      {mode === 'single' ? (
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">Predictor</label>
          <select
            value={predictorType}
            onChange={(e) => setPredictorType(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-md bg-white"
          >
            {PREDICTORS.map((p) => (
              <option key={p.value} value={p.value}>{p.label}</option>
            ))}
          </select>
        </div>
      ) : (
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-2">Predictors to compare</label>
          <div className="flex flex-wrap gap-2">
            {PREDICTORS.map((p) => (
              <label key={p.value} className="flex items-center gap-2 px-3 py-1.5 border border-slate-300 rounded-md text-sm cursor-pointer hover:bg-slate-50">
                <input
                  type="checkbox"
                  checked={selected.includes(p.value)}
                  onChange={() => toggle(p.value)}
                />
                {p.label}
              </label>
            ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">Table size</label>
          <input
            type="number"
            min="2"
            value={tableSize}
            onChange={(e) => setTableSize(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-md"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">History bits</label>
          <input
            type="number"
            min="1"
            max="20"
            value={historyBits}
            onChange={(e) => setHistoryBits(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-md"
          />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-slate-700 mb-1">Trace content</label>
        <textarea
          value={traceContent}
          onChange={(e) => setTraceContent(e.target.value)}
          rows={6}
          className="w-full px-3 py-2 border border-slate-300 rounded-md font-mono text-xs"
          placeholder="0x400000 1"
        />
        <p className="text-xs text-slate-500 mt-1">Format: <code>PC_hex outcome</code> per line (1 = taken, 0 = not taken)</p>
      </div>

      <button
        type="submit"
        disabled={loading || (mode === 'compare' && selected.length === 0)}
        className="w-full bg-indigo-600 text-white px-4 py-2 rounded-md font-medium hover:bg-indigo-700 disabled:bg-slate-300 disabled:cursor-not-allowed"
      >
        {loading ? 'Running…' : mode === 'compare' ? 'Compare predictors' : 'Run predictor'}
      </button>
    </form>
  )
}
