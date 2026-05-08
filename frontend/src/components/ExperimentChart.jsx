import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

function CustomTooltip({ active, payload }) {
  if (!active || !payload || payload.length === 0) return null
  const row = payload[0].payload
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-xs text-gray-100 shadow-lg">
      <div className="font-semibold text-white mb-1">tableSize = {row.tableSize.toLocaleString()}</div>
      <div className="text-blue-400">
        Misprediction rate: <span className="tabular-nums">{Number(row.mispredictionRate).toFixed(2)}%</span>
      </div>
      <div className="text-emerald-400">
        MPKI: <span className="tabular-nums">{Number(row.mpki).toFixed(3)}</span>
      </div>
    </div>
  )
}

export default function ExperimentChart({ data, optimalRate, saturationTableSize }) {
  if (!data || !Array.isArray(data) || data.length === 0) return null

  const validData = data.filter(
    (p) => p != null && typeof p.tableSize === 'number' && typeof p.mispredictionRate === 'number'
  )
  if (validData.length === 0) return null

  const saturationLabel = saturationTableSize != null
    ? String(saturationTableSize.toLocaleString())
    : null

  return (
    <div className="bg-gray-800 rounded-lg border border-gray-700 p-5">
      <h3 className="text-lg font-semibold text-white mb-4">
        Misprediction rate &amp; MPKI vs Table Size
      </h3>
      <ResponsiveContainer width="100%" height={360}>
        <LineChart data={validData} margin={{ top: 16, right: 40, left: 8, bottom: 24 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#374151" />

          {/* Категориальная ось — не крашится в отличие от scale="log" */}
          <XAxis
            dataKey="tableSize"
            tickFormatter={(v) => {
              if (v >= 1024) return `${v / 1024}K`
              return String(v)
            }}
            stroke="#9ca3af"
            tick={{ fontSize: 11, fill: '#d1d5db' }}
            label={{ value: 'Table size', position: 'insideBottom', offset: -12, fill: '#9ca3af', fontSize: 12 }}
          />

          <YAxis
            yAxisId="left"
            orientation="left"
            stroke="#3b82f6"
            tick={{ fontSize: 11, fill: '#93c5fd' }}
            tickFormatter={(v) => `${v}%`}
            label={{ value: 'Rate (%)', angle: -90, position: 'insideLeft', fill: '#3b82f6', style: { textAnchor: 'middle' }, fontSize: 12 }}
          />
          <YAxis
            yAxisId="right"
            orientation="right"
            stroke="#10b981"
            tick={{ fontSize: 11, fill: '#6ee7b7' }}
            label={{ value: 'MPKI', angle: 90, position: 'insideRight', fill: '#10b981', style: { textAnchor: 'middle' }, fontSize: 12 }}
          />

          <Tooltip content={<CustomTooltip />} cursor={{ stroke: '#6b7280', strokeWidth: 1 }} />
          <Legend wrapperStyle={{ paddingTop: 16, color: '#d1d5db', fontSize: 12 }} />

          {typeof optimalRate === 'number' && (
            <ReferenceLine
              yAxisId="left"
              y={optimalRate}
              stroke="#a3e635"
              strokeDasharray="4 4"
              label={{ value: `Best ${optimalRate.toFixed(1)}%`, position: 'insideTopRight', fill: '#a3e635', fontSize: 11 }}
            />
          )}

          {saturationLabel != null && (
            <ReferenceLine
              yAxisId="left"
              x={saturationTableSize}
              stroke="#f59e0b"
              strokeDasharray="6 4"
              label={{ value: 'Saturation', position: 'top', fill: '#f59e0b', fontSize: 11 }}
            />
          )}

          <Line
            yAxisId="left"
            type="monotone"
            dataKey="mispredictionRate"
            name="Misprediction rate (%)"
            stroke="#3b82f6"
            strokeWidth={2}
            dot={{ r: 4, fill: '#3b82f6' }}
            activeDot={{ r: 6 }}
          />
          <Line
            yAxisId="right"
            type="monotone"
            dataKey="mpki"
            name="MPKI"
            stroke="#10b981"
            strokeWidth={2}
            dot={{ r: 4, fill: '#10b981' }}
            activeDot={{ r: 6 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
