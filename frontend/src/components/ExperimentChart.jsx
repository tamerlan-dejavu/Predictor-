import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

export default function ExperimentChart({ data, metric = 'mispredictionRate' }) {
  if (!data || data.length === 0) return null

  const chartData = data.map((p) => ({
    tableSize: p.tableSize,
    mispredictionRate: Number(p.mispredictionRate.toFixed(2)),
    mpki: Number(p.mpki.toFixed(3)),
  }))

  const isRate = metric === 'mispredictionRate'
  const dataKey = isRate ? 'mispredictionRate' : 'mpki'
  const label = isRate ? 'Misprediction rate (%)' : 'MPKI'
  const formatter = isRate ? (v) => `${v}%` : (v) => v

  return (
    <div className="bg-gray-800 rounded-lg border border-gray-700 p-5">
      <h3 className="text-lg font-semibold text-white mb-4">{label} vs table size</h3>
      <ResponsiveContainer width="100%" height={340}>
        <LineChart data={chartData} margin={{ top: 16, right: 16, left: 0, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
          <XAxis
            dataKey="tableSize"
            scale="log"
            domain={['dataMin', 'dataMax']}
            stroke="#9ca3af"
            tick={{ fontSize: 12, fill: '#d1d5db' }}
          />
          <YAxis stroke="#9ca3af" tick={{ fontSize: 12, fill: '#d1d5db' }} />
          <Tooltip
            contentStyle={{ backgroundColor: '#111827', border: '1px solid #374151', borderRadius: 6, color: '#f3f4f6' }}
            formatter={formatter}
            labelFormatter={(v) => `Table size: ${v}`}
          />
          <Legend wrapperStyle={{ paddingTop: 12, color: '#d1d5db' }} />
          <Line
            type="monotone"
            dataKey={dataKey}
            name={label}
            stroke="#3b82f6"
            strokeWidth={2}
            dot={{ r: 4, fill: '#3b82f6' }}
            activeDot={{ r: 6 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
