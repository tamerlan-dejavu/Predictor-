import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#a855f7', '#06b6d4']

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

function CustomTooltip({ active, payload }) {
  if (!active || !payload || payload.length === 0) return null
  const row = payload[0].payload
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-xs text-gray-100 shadow-lg">
      <div className="font-semibold text-white mb-1">{row.fullName}</div>
      <div className="text-gray-300">Total: <span className="tabular-nums">{row.totalPredictions.toLocaleString()}</span></div>
      <div className="text-gray-300">Misses: <span className="tabular-nums">{row.mispredictions.toLocaleString()}</span></div>
      <div className="text-gray-300">Rate: <span className="tabular-nums text-blue-400">{row.mispredictionRate.toFixed(2)}%</span></div>
      <div className="text-gray-300">MPKI: <span className="tabular-nums text-blue-400">{row.mpki.toFixed(3)}</span></div>
    </div>
  )
}

export default function ComparisonChart({ data }) {
  if (!data || data.length === 0) return null

  const chartData = data.map((r) => ({
    name: abbreviateName(r.predictorName),
    fullName: r.predictorName,
    totalPredictions: r.totalPredictions,
    mispredictions: r.mispredictions,
    mispredictionRate: r.mispredictionRate,
    mpki: r.mpki,
  }))

  return (
    <div className="bg-gray-800 rounded-lg border border-gray-700 p-5 space-y-3">
      <div>
        <h3 className="text-lg font-semibold text-white">Misprediction Rate by Predictor</h3>
        <p className="text-xs text-gray-500">Hover over bars for full predictor names</p>
      </div>
      <ResponsiveContainer width="100%" height={360}>
        <BarChart data={chartData} margin={{ top: 16, right: 16, left: 0, bottom: 40 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
          <XAxis dataKey="name" stroke="#9ca3af" tick={{ fontSize: 12, fill: '#d1d5db' }} />
          <YAxis
            stroke="#9ca3af"
            tick={{ fontSize: 12, fill: '#d1d5db' }}
            label={{ value: 'Misprediction rate (%)', angle: -90, position: 'insideLeft', fill: '#9ca3af', style: { textAnchor: 'middle' } }}
          />
          <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(59, 130, 246, 0.08)' }} />
          <Legend wrapperStyle={{ paddingTop: 12, color: '#d1d5db' }} />
          <Bar dataKey="mispredictionRate" name="Misprediction rate (%)" radius={[6, 6, 0, 0]}>
            {chartData.map((_, i) => (
              <Cell key={i} fill={COLORS[i % COLORS.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
