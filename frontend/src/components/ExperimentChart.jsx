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
    mispredictionRate: Number((p.mispredictionRate * 100).toFixed(2)),
    mpki: Number(p.mpki.toFixed(3)),
  }))

  const isRate = metric === 'mispredictionRate'
  const dataKey = isRate ? 'mispredictionRate' : 'mpki'
  const label = isRate ? 'Misprediction rate (%)' : 'MPKI'
  const formatter = isRate ? (v) => `${v}%` : (v) => v

  return (
    <div className="bg-white rounded-lg border border-slate-200 p-6">
      <h3 className="text-lg font-semibold text-slate-900 mb-4">{label} vs table size</h3>
      <ResponsiveContainer width="100%" height={320}>
        <LineChart data={chartData} margin={{ top: 16, right: 16, left: 0, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey="tableSize" scale="log" domain={['dataMin', 'dataMax']} tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} />
          <Tooltip formatter={formatter} labelFormatter={(v) => `Table size: ${v}`} />
          <Legend />
          <Line
            type="monotone"
            dataKey={dataKey}
            name={label}
            stroke="#6366f1"
            strokeWidth={2}
            dot={{ r: 4 }}
            activeDot={{ r: 6 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
