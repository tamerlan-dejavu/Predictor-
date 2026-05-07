import { useState } from 'react'
import ExperimentChart from '../components/ExperimentChart.jsx'
import LoadingSpinner from '../components/LoadingSpinner.jsx'
import { getExperiment } from '../api/predictorApi.js'

const PREDICTORS = [
  { value: 'bimodal', label: 'Bimodal' },
  { value: 'gshare', label: 'GShare' },
  { value: 'tournament', label: 'Tournament' },
]

export default function ExperimentPage() {
  const [predictor, setPredictor] = useState('gshare')
  const [minTable, setMinTable] = useState(16)
  const [maxTable, setMaxTable] = useState(4096)
  const [steps, setSteps] = useState(9)
  const [metric, setMetric] = useState('mispredictionRate')
  const [points, setPoints] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const { data } = await getExperiment({
        predictor,
        minTable: Number(minTable),
        maxTable: Number(maxTable),
        steps: Number(steps),
      })
      setPoints(data)
    } catch (err) {
      setError(err?.response?.data?.error || err.message || 'Request failed')
      setPoints([])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-900">Table size sweep</h2>
        <p className="text-slate-600 text-sm mt-1">
          Sweep table size geometrically (×2 per step) and chart how misprediction rate or MPKI scales.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-lg border border-slate-200 p-6 grid md:grid-cols-5 gap-4">
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">Predictor</label>
          <select
            value={predictor}
            onChange={(e) => setPredictor(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-md bg-white"
          >
            {PREDICTORS.map((p) => (
              <option key={p.value} value={p.value}>{p.label}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">Min table</label>
          <input
            type="number"
            value={minTable}
            onChange={(e) => setMinTable(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-md"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">Max table</label>
          <input
            type="number"
            value={maxTable}
            onChange={(e) => setMaxTable(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-md"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">Steps</label>
          <input
            type="number"
            min="1"
            max="20"
            value={steps}
            onChange={(e) => setSteps(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-md"
          />
        </div>
        <div className="flex items-end">
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-indigo-600 text-white px-4 py-2 rounded-md font-medium hover:bg-indigo-700 disabled:bg-slate-300"
          >
            {loading ? 'Running…' : 'Run sweep'}
          </button>
        </div>
      </form>

      <div className="flex gap-2">
        <button
          onClick={() => setMetric('mispredictionRate')}
          className={`px-3 py-1.5 rounded-md text-sm font-medium ${
            metric === 'mispredictionRate' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-700 border border-slate-300'
          }`}
        >
          Misprediction rate
        </button>
        <button
          onClick={() => setMetric('mpki')}
          className={`px-3 py-1.5 rounded-md text-sm font-medium ${
            metric === 'mpki' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-700 border border-slate-300'
          }`}
        >
          MPKI
        </button>
      </div>

      {loading && <LoadingSpinner label="Sweeping table sizes…" />}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-800 rounded-md px-4 py-3 text-sm">
          {error}
        </div>
      )}
      {!loading && !error && points.length === 0 && (
        <div className="bg-white border border-dashed border-slate-300 rounded-lg p-8 text-center text-slate-500 text-sm">
          Run a sweep to see results.
        </div>
      )}
      {points.length > 0 && <ExperimentChart data={points} metric={metric} />}
    </div>
  )
}
