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
      setError(err?.response?.data?.error || err.message || 'Не удалось выполнить запрос')
      setPoints([])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-2xl font-bold text-white">Table size sweep</h2>
        <p className="text-gray-400 text-sm mt-1">
          Геометрический сдвиг table size (×2 на шаг) — смотрим, как масштабируется misprediction rate / MPKI.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-gray-800 rounded-lg border border-gray-700 p-5 grid md:grid-cols-5 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-1">Predictor</label>
          <select
            value={predictor}
            onChange={(e) => setPredictor(e.target.value)}
            className="w-full px-3 py-2 border border-gray-700 rounded-md bg-gray-900 text-gray-100 focus:outline-none focus:border-blue-500"
          >
            {PREDICTORS.map((p) => (
              <option key={p.value} value={p.value}>{p.label}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-1">Min table</label>
          <input
            type="number"
            value={minTable}
            onChange={(e) => setMinTable(e.target.value)}
            className="w-full px-3 py-2 border border-gray-700 rounded-md bg-gray-900 text-gray-100 focus:outline-none focus:border-blue-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-1">Max table</label>
          <input
            type="number"
            value={maxTable}
            onChange={(e) => setMaxTable(e.target.value)}
            className="w-full px-3 py-2 border border-gray-700 rounded-md bg-gray-900 text-gray-100 focus:outline-none focus:border-blue-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-1">Steps</label>
          <input
            type="number"
            min="1"
            max="20"
            value={steps}
            onChange={(e) => setSteps(e.target.value)}
            className="w-full px-3 py-2 border border-gray-700 rounded-md bg-gray-900 text-gray-100 focus:outline-none focus:border-blue-500"
          />
        </div>
        <div className="flex items-end">
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white px-4 py-2 rounded-md font-medium hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500"
          >
            {loading ? 'Запуск…' : 'Run sweep'}
          </button>
        </div>
      </form>

      <div className="flex gap-2">
        <button
          onClick={() => setMetric('mispredictionRate')}
          className={`px-3 py-1.5 rounded-md text-sm font-medium ${
            metric === 'mispredictionRate'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-800 text-gray-300 border border-gray-700 hover:bg-gray-700'
          }`}
        >
          Misprediction rate
        </button>
        <button
          onClick={() => setMetric('mpki')}
          className={`px-3 py-1.5 rounded-md text-sm font-medium ${
            metric === 'mpki'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-800 text-gray-300 border border-gray-700 hover:bg-gray-700'
          }`}
        >
          MPKI
        </button>
      </div>

      {loading && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
          <LoadingSpinner label="Свипуем table size…" />
        </div>
      )}
      {error && (
        <div className="bg-red-950/60 border border-red-700 text-red-200 rounded-md px-4 py-3 text-sm">
          {error}
        </div>
      )}
      {!loading && !error && points.length === 0 && (
        <div className="bg-gray-800 border border-dashed border-gray-700 rounded-lg p-12 text-center text-gray-400 text-sm">
          Запустите свип, чтобы увидеть результаты.
        </div>
      )}
      {points.length > 0 && <ExperimentChart data={points} metric={metric} />}
    </div>
  )
}
