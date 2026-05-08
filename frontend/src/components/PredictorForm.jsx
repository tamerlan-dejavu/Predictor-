import { useMemo, useState } from 'react'

const PREDICTORS = [
  { value: 'bimodal',      label: 'Bimodal',     usesHistory: false },
  { value: 'gshare',       label: 'GShare',      usesHistory: true  },
  { value: 'tournament',   label: 'Tournament',  usesHistory: true  },
  { value: 'static_taken', label: 'Static (AT)', usesHistory: false },
  { value: 'static_nt',    label: 'Static (NT)', usesHistory: false },
]

// loop_10: 10 iterations of (9 taken + 1 not-taken) at 0x400000
const LOOP_10_TRACE = (() => {
  let s = ''
  for (let i = 0; i < 10; i++) {
    for (let j = 0; j < 9; j++) s += '0x400000 1\n'
    s += '0x400000 0\n'
  }
  return s
})()

const log2 = (n) => Math.round(Math.log2(n))

export default function PredictorForm({ onSubmit, loading = false }) {
  const [selected, setSelected] = useState(['bimodal', 'gshare', 'tournament'])
  const [tableExp, setTableExp] = useState(10)   // 2^10 = 1024
  const [historyBits, setHistoryBits] = useState(8)
  const [traceContent, setTraceContent] = useState('')

  const tableSize = useMemo(() => 2 ** tableExp, [tableExp])
  const showHistory = useMemo(
    () => selected.some((v) => PREDICTORS.find((p) => p.value === v)?.usesHistory),
    [selected]
  )

  const toggle = (val) =>
    setSelected((prev) =>
      prev.includes(val) ? prev.filter((p) => p !== val) : [...prev, val]
    )

  const loadSample = () => setTraceContent(LOOP_10_TRACE)

  const handleSubmit = (e) => {
    e.preventDefault()
    if (selected.length === 0 || !traceContent.trim()) return
    onSubmit({
      predictors: selected,
      tableSize,
      historyBits: Number(historyBits),
      traceContent,
    })
  }

  const canSubmit = selected.length > 0 && traceContent.trim().length > 0 && !loading

  return (
    <form onSubmit={handleSubmit} className="bg-gray-800 rounded-lg border border-gray-700 p-5 space-y-5">
      <h2 className="text-lg font-semibold text-white">Run Parameters</h2>

      {/* Predictor multiselect */}
      <div>
        <label className="block text-sm font-medium text-gray-300 mb-2">Predictors</label>
        <div className="space-y-1.5">
          {PREDICTORS.map((p) => (
            <label
              key={p.value}
              className="flex items-center gap-2 px-3 py-1.5 rounded-md text-sm cursor-pointer text-gray-200 hover:bg-gray-700"
            >
              <input
                type="checkbox"
                checked={selected.includes(p.value)}
                onChange={() => toggle(p.value)}
                className="w-4 h-4 accent-blue-500"
              />
              {p.label}
            </label>
          ))}
        </div>
      </div>

      {/* Table size slider */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-sm font-medium text-gray-300">Table Size</label>
          <span className="text-sm tabular-nums text-blue-400 font-mono">
            2<sup>{tableExp}</sup> = {tableSize.toLocaleString()}
          </span>
        </div>
        <input
          type="range"
          min={4}
          max={16}
          step={1}
          value={tableExp}
          onChange={(e) => setTableExp(Number(e.target.value))}
          className="w-full accent-blue-500"
        />
        <div className="flex justify-between text-xs text-gray-500 mt-0.5">
          <span>16</span>
          <span>65 536</span>
        </div>
      </div>

      {/* History bits slider — only when GShare or Tournament selected */}
      {showHistory && (
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-sm font-medium text-gray-300">History Bits</label>
            <span className="text-sm tabular-nums text-blue-400 font-mono">{historyBits}</span>
          </div>
          <input
            type="range"
            min={2}
            max={16}
            step={1}
            value={historyBits}
            onChange={(e) => setHistoryBits(Number(e.target.value))}
            className="w-full accent-blue-500"
          />
          <div className="flex justify-between text-xs text-gray-500 mt-0.5">
            <span>2</span>
            <span>16</span>
          </div>
        </div>
      )}

      {/* Trace textarea */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-sm font-medium text-gray-300">Trace</label>
          <button
            type="button"
            onClick={loadSample}
            className="text-xs text-blue-400 hover:text-blue-300 underline"
          >
            Load sample trace
          </button>
        </div>
        <textarea
          value={traceContent}
          onChange={(e) => setTraceContent(e.target.value)}
          rows={6}
          placeholder="0x400000 1&#10;0x400000 0"
          className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-md font-mono text-xs text-gray-100 placeholder-gray-600 focus:outline-none focus:border-blue-500"
        />
        <p className="text-xs text-gray-500 mt-1">
          Format: <code className="text-gray-400">PC_hex outcome</code> (1 = taken, 0 = not taken)
        </p>
      </div>

      <button
        type="submit"
        disabled={!canSubmit}
        className="w-full bg-blue-600 text-white px-4 py-2.5 rounded-md font-medium hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed transition-colors"
      >
        {loading ? 'Running…' : 'Run Comparison'}
      </button>
    </form>
  )
}
