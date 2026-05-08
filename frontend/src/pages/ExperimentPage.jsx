import { useMemo, useState } from 'react'
import ExperimentChart from '../components/ExperimentChart.jsx'
import LoadingSpinner from '../components/LoadingSpinner.jsx'
import { comparePredictors } from '../api/predictorApi.js'

const PREDICTORS = [
  { value: 'bimodal',    label: 'Bimodal' },
  { value: 'gshare',     label: 'GShare' },
  { value: 'tournament', label: 'Tournament' },
]

// LOOP_10_TRACE: 10 iterations of (9 taken + 1 not-taken) = 100 branches total
const LOOP_10_TRACE = (() => {
  let s = ''
  for (let i = 0; i < 10; i++) {
    for (let j = 0; j < 9; j++) s += '0x400000 1\n'
    s += '0x400000 0\n'
  }
  return s
})()

const TABLE_SIZES = [16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536]
const SATURATION_THRESHOLD_PCT = 1.0

function findSaturationPoint(points) {
  if (!points || points.length < 2) return null
  const minRate = Math.min(...points.map((p) => p.mispredictionRate))
  for (let i = 0; i < points.length; i++) {
    const tail = points.slice(i)
    if (tail.every((p) => Math.abs(p.mispredictionRate - minRate) <= SATURATION_THRESHOLD_PCT)) {
      return points[i]
    }
  }
  return null
}

function findBest(points) {
  if (!points || points.length === 0) return null
  return points.reduce((best, p) => (p.mispredictionRate < best.mispredictionRate ? p : best), points[0])
}

export default function ExperimentPage() {
  const [predictor, setPredictor] = useState('bimodal')
  const [points, setPoints] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const handleRun = async () => {
    setLoading(true)
    setError(null)
    setPoints([])

    try {
      const experimentPoints = []

      // Run predictor on LOOP_10_TRACE with different table sizes
      for (const tableSize of TABLE_SIZES) {
        try {
          const response = await comparePredictors({
            predictors: [predictor],
            tableSize,
            historyBits: 8,
            traceContent: LOOP_10_TRACE,
          })

          if (response.data && response.data.length > 0) {
            const result = response.data[0]
            experimentPoints.push({
              tableSize,
              mispredictionRate: result.mispredictionRate,
              mpki: result.mpki,
              name: result.predictorName,
            })
          }
        } catch (err) {
          console.warn(`Failed for tableSize ${tableSize}:`, err.message)
          // Continue with next table size
        }
      }

      if (experimentPoints.length === 0) {
        throw new Error('No valid data points returned from experiment')
      }

      setPoints(experimentPoints)
    } catch (err) {
      console.error('Experiment error:', err)
      const errorMsg = err?.response?.data?.error || err?.message || 'Failed to run experiment'
      setError(errorMsg)
    } finally {
      setLoading(false)
    }
  }

  const best = useMemo(() => findBest(points), [points])
  const saturation = useMemo(() => findSaturationPoint(points), [points])
  const predictorLabel = PREDICTORS.find((p) => p.value === predictor)?.label || predictor

  return (
    <div className="space-y-5">
      <header>
        <h2 className="text-2xl font-bold text-white">Experiment: Accuracy vs. Table Size</h2>
        <p className="text-gray-400 text-sm mt-1">
          Sweep tableSize by log2 (×2 per step) — observe saturation point where accuracy improvement plateaus.
        </p>
      </header>

      <section className="bg-gray-800 rounded-lg border border-gray-700 p-5 space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-2">Predictor</label>
          <div className="flex flex-wrap gap-2">
            {PREDICTORS.map((p) => (
              <label
                key={p.value}
                className={`flex items-center gap-2 px-3 py-2 rounded-md border cursor-pointer text-sm transition-colors ${
                  predictor === p.value
                    ? 'border-blue-500 bg-blue-500/10 text-white'
                    : 'border-gray-700 text-gray-300 hover:bg-gray-700/50'
                }`}
              >
                <input
                  type="radio"
                  name="predictor"
                  value={p.value}
                  checked={predictor === p.value}
                  onChange={() => setPredictor(p.value)}
                  className="accent-blue-500"
                />
                {p.label}
              </label>
            ))}
          </div>
        </div>

        <div className="text-xs text-gray-500">
          <div>Trace: <span className="font-mono text-gray-400">LOOP_10</span> (100 branches: 90 taken, 10 not-taken)</div>
          <div>Table sizes: <span className="font-mono text-gray-400">16 to 65536</span> ({TABLE_SIZES.length} points)</div>
          <div>History bits: <span className="font-mono text-gray-400">8</span> (for GShare & Tournament)</div>
        </div>

        <button
          type="button"
          onClick={handleRun}
          disabled={loading}
          className="w-full md:w-auto bg-blue-600 text-white px-5 py-2.5 rounded-md font-medium hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 transition-colors"
        >
          {loading ? 'Running…' : 'Run Experiment'}
        </button>
      </section>

      {error && (
        <div className="bg-red-950/60 border border-red-700 text-red-200 rounded-md px-4 py-3 text-sm space-y-2">
          <div>
            <span className="font-semibold text-red-300">Error:</span> {error}
          </div>
          <div className="text-xs text-red-300 mt-2">
            Troubleshooting: Open DevTools (F12) → Network tab → click Run Experiment again to see API response
          </div>
        </div>
      )}

      {loading && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
          <LoadingSpinner label={`Sweeping table size for ${predictorLabel}…`} />
        </div>
      )}

      {!loading && !error && points.length === 0 && (
        <div className="bg-gray-800 border border-dashed border-gray-700 rounded-lg p-12 text-center text-gray-400 text-sm">
          Run the experiment to see the graph and interpretation.
        </div>
      )}

      {!loading && points.length > 0 && (
        <>
          <ExperimentChart
            data={points}
            optimalRate={best?.mispredictionRate}
            saturationTableSize={saturation?.tableSize}
          />

          <section className="bg-gray-800 rounded-lg border border-gray-700 p-5 space-y-3">
            <h3 className="text-lg font-semibold text-white">Interpretation</h3>
            {best && (
              <div className="text-sm text-gray-200">
                <span className="text-gray-400">Best configuration: </span>
                <span className="font-mono text-blue-400">tableSize = {best.tableSize.toLocaleString()}</span>
                {' '}yields{' '}
                <span className="font-mono text-blue-400">{best.mispredictionRate.toFixed(2)}%</span>
                {' '}misprediction (MPKI <span className="font-mono text-emerald-400">{best.mpki.toFixed(3)}</span>).
              </div>
            )}
            {saturation && best && (
              <>
                <div className="text-sm text-gray-200">
                  <span className="text-gray-400">Saturation point: </span>
                  at <span className="font-mono text-amber-400">tableSize = {saturation.tableSize.toLocaleString()}</span>
                  {' '}deviation from optimal is &lt; {SATURATION_THRESHOLD_PCT.toFixed(1)} pp.
                </div>
                <div className="text-sm text-gray-200 pt-1 border-t border-gray-700">
                  <span className="text-gray-400">Conclusion: </span>
                  {saturation.tableSize === points[0].tableSize ? (
                    <>trace is too short — even smallest table is sufficient, larger sizes bring no improvement.</>
                  ) : (
                    <>increasing table size beyond{' '}
                      <span className="font-mono text-amber-400">{saturation.tableSize.toLocaleString()}</span>
                      {' '}brings no significant accuracy gain.
                    </>
                  )}
                </div>
              </>
            )}
            {!saturation && best && (
              <div className="text-sm text-gray-200 pt-1 border-t border-gray-700">
                <span className="text-gray-400">Conclusion: </span>
                rate continues improving noticeably — no saturation in tested range, consider increasing{' '}
                <span className="font-mono">maxTable</span>.
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
