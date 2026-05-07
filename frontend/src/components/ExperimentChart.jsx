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

const POW2_TICKS = [16, 64, 256, 1024, 4096, 16384, 65536]

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload || payload.length === 0) return null
  const row = payload[0].payload
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-xs text-gray-100 shadow-lg">
      <div className="font-semibold text-white mb-1 tabular-nums">tableSize = {row.tableSize.toLocaleString()}</div>
      <div className="text-blue-400">Misprediction rate: <span className="tabular-nums">{row.mispredictionRate.toFixed(2)}%</span></div>
      <div className="text-emerald-400">MPKI: <span className="tabular-nums">{row.mpki.toFixed(3)}</span></div>
    </div>
  )
}

export default function ExperimentChart({ data, optimalRate, saturationTableSize }) {
  if (!data || data.length === 0) return null

  const ticks = POW2_TICKS.filter((t) => t >= data[0].tableSize && t <= data[data.length - 1].tableSize)

  return (
    <div className="bg-gray-800 rounded-lg border border-gray-700 p-5">
      <h3 className="text-lg font-semibold text-white mb-4">
        Misprediction rate &amp; MPKI vs table size
      </h3>
      <ResponsiveContainer width="100%" height={380}>
        <LineChart data={data} margin={{ top: 16, right: 32, left: 8, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
          <XAxis
            dataKey="tableSize"
            type="number"
            scale="log"
            domain={['dataMin', 'dataMax']}
            ticks={ticks.length ? ticks : undefined}
            tickFormatter={(v) => v.toLocaleString()}
            stroke="#9ca3af"
            tick={{ fontSize: 12, fill: '#d1d5db' }}
            label={{ value: 'Table size (log scale)', position: 'insideBottom', offset: -4, fill: '#9ca3af' }}
          />
          <YAxis
            yAxisId="left"
            orientation="left"
            stroke="#3b82f6"
            tick={{ fontSize: 12, fill: '#93c5fd' }}
            label={{ value: 'Rate (%)', angle: -90, position: 'insideLeft', fill: '#3b82f6', style: { textAnchor: 'middle' } }}
          />
          <YAxis
            yAxisId="right"
            orientation="right"
            stroke="#10b981"
            tick={{ fontSize: 12, fill: '#6ee7b7' }}
            label={{ value: 'MPKI', angle: 90, position: 'insideRight', fill: '#10b981', style: { textAnchor: 'middle' } }}
          />
          <Tooltip content={<CustomTooltip />} cursor={{ stroke: '#6b7280', strokeWidth: 1 }} />
          <Legend wrapperStyle={{ paddingTop: 12, color: '#d1d5db' }} />

          {typeof optimalRate === 'number' && (
            <ReferenceLine
              yAxisId="left"
              y={optimalRate}
              stroke="#a3e635"
              strokeDasharray="4 4"
              label={{ value: `Оптимум ${optimalRate.toFixed(2)}%`, position: 'right', fill: '#a3e635', fontSize: 11 }}
            />
          )}

          {typeof saturationTableSize === 'number' && (
            <ReferenceLine
              x={saturationTableSize}
              stroke="#f59e0b"
              strokeDasharray="6 4"
              label={{ value: 'насыщение', position: 'top', fill: '#f59e0b', fontSize: 11 }}
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
