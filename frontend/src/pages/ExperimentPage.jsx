import { useMemo, useState } from 'react'
import ExperimentChart from '../components/ExperimentChart.jsx'
import LoadingSpinner from '../components/LoadingSpinner.jsx'
import { getExperiment } from '../api/predictorApi.js'

const PREDICTORS = [
  { value: 'bimodal',    label: 'Bimodal' },
  { value: 'gshare',     label: 'GShare' },
  { value: 'tournament', label: 'Tournament' },
]

const DEFAULT_PARAMS = { minTable: 16, maxTable: 65536, steps: 8 }
const SATURATION_THRESHOLD_PCT = 1.0  // абсолютная разница в процентных пунктах

// Точка насыщения: наименьший tableSize, начиная с которого rate
// перестаёт значимо (>1 п.п.) отличаться от глобального минимума.
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
  const [predictor, setPredictor] = useState('gshare')
  const [points, setPoints] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const handleRun = async () => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await getExperiment({ predictor, ...DEFAULT_PARAMS })
      const sorted = [...data].sort((a, b) => a.tableSize - b.tableSize)
      setPoints(sorted)
    } catch (err) {
      setError(err?.response?.data?.error || err.message || 'Не удалось выполнить запрос')
      setPoints([])
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
        <h2 className="text-2xl font-bold text-white">Эксперимент: зависимость точности от размера таблицы</h2>
        <p className="text-gray-400 text-sm mt-1">
          Свип tableSize по log2 (×2 на шаг) — смотрим, до какого размера выгодно расти, прежде чем начнётся насыщение.
        </p>
      </header>

      <section className="bg-gray-800 rounded-lg border border-gray-700 p-5 space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-2">Предсказатель</label>
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
          Параметры запроса: <span className="font-mono text-gray-400">minTable={DEFAULT_PARAMS.minTable},
          maxTable={DEFAULT_PARAMS.maxTable.toLocaleString()}, steps={DEFAULT_PARAMS.steps}</span>
        </div>

        <button
          type="button"
          onClick={handleRun}
          disabled={loading}
          className="w-full md:w-auto bg-blue-600 text-white px-5 py-2.5 rounded-md font-medium hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 transition-colors"
        >
          {loading ? 'Запуск…' : 'Запустить эксперимент'}
        </button>
      </section>

      {error && (
        <div className="bg-red-950/60 border border-red-700 text-red-200 rounded-md px-4 py-3 text-sm">
          <span className="font-semibold text-red-300">Ошибка:</span> {error}
        </div>
      )}

      {loading && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
          <LoadingSpinner label={`Свипуем table size для ${predictorLabel}…`} />
        </div>
      )}

      {!loading && !error && points.length === 0 && (
        <div className="bg-gray-800 border border-dashed border-gray-700 rounded-lg p-12 text-center text-gray-400 text-sm">
          Запустите эксперимент, чтобы увидеть график и интерпретацию.
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
            <h3 className="text-lg font-semibold text-white">Интерпретация</h3>
            {best && (
              <div className="text-sm text-gray-200">
                <span className="text-gray-400">Лучшая конфигурация: </span>
                <span className="font-mono text-blue-400">tableSize = {best.tableSize.toLocaleString()}</span>
                {' '}даёт{' '}
                <span className="font-mono text-blue-400">{best.mispredictionRate.toFixed(2)}%</span>
                {' '}misprediction (MPKI <span className="font-mono text-emerald-400">{best.mpki.toFixed(3)}</span>).
              </div>
            )}
            {saturation && best && (
              <>
                <div className="text-sm text-gray-200">
                  <span className="text-gray-400">Точка насыщения: </span>
                  при <span className="font-mono text-amber-400">tableSize = {saturation.tableSize.toLocaleString()}</span>
                  {' '}отклонение от оптимума уже &lt; {SATURATION_THRESHOLD_PCT.toFixed(1)} п.п.
                </div>
                <div className="text-sm text-gray-200 pt-1 border-t border-gray-700">
                  <span className="text-gray-400">Вывод: </span>
                  {saturation.tableSize === points[0].tableSize ? (
                    <>трасса слишком короткая — даже самой маленькой таблицы достаточно, увеличение размера ничего не даёт.</>
                  ) : (
                    <>увеличение таблицы выше{' '}
                      <span className="font-mono text-amber-400">{saturation.tableSize.toLocaleString()}</span>
                      {' '}значимого выигрыша по точности не приносит.
                    </>
                  )}
                </div>
              </>
            )}
            {!saturation && best && (
              <div className="text-sm text-gray-200 pt-1 border-t border-gray-700">
                <span className="text-gray-400">Вывод: </span>
                rate продолжает заметно улучшаться — насыщения в исследованном диапазоне нет, имеет смысл увеличить{' '}
                <span className="font-mono">maxTable</span>.
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
