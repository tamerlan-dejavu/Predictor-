import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

export default function ComparisonChart({ data }) {
  if (!data || data.length === 0) return null

  const chartData = data.map((r) => ({
    name: r.predictorName,
    mispredictionRate: Number((r.mispredictionRate * 100).toFixed(2)),
    mpki: Number(r.mpki.toFixed(3)),
  }))

  return (
    <div className="bg-white rounded-lg border border-slate-200 p-6">
      <h3 className="text-lg font-semibold text-slate-900 mb-4">Misprediction rate by predictor</h3>
      <ResponsiveContainer width="100%" height={320}>
        <BarChart data={chartData} margin={{ top: 16, right: 16, left: 0, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey="name" tick={{ fontSize: 12 }} />
          <YAxis label={{ value: '%', position: 'insideLeft', angle: -90 }} tick={{ fontSize: 12 }} />
          <Tooltip formatter={(v) => `${v}%`} />
          <Legend />
          <Bar dataKey="mispredictionRate" name="Misprediction rate (%)" fill="#6366f1" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
