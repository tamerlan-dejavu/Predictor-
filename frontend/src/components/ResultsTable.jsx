export default function ResultsTable({ rows }) {
  if (!rows || rows.length === 0) return null

  return (
    <div className="bg-white rounded-lg border border-slate-200 overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 text-slate-700">
          <tr>
            <th className="text-left px-4 py-2 font-medium">Predictor</th>
            <th className="text-right px-4 py-2 font-medium">Predictions</th>
            <th className="text-right px-4 py-2 font-medium">Mispredictions</th>
            <th className="text-right px-4 py-2 font-medium">Rate</th>
            <th className="text-right px-4 py-2 font-medium">MPKI</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={`${r.predictorName}-${i}`} className="border-t border-slate-200">
              <td className="px-4 py-2 font-medium text-slate-900">{r.predictorName}</td>
              <td className="px-4 py-2 text-right tabular-nums">{r.totalPredictions.toLocaleString()}</td>
              <td className="px-4 py-2 text-right tabular-nums">{r.mispredictions.toLocaleString()}</td>
              <td className="px-4 py-2 text-right tabular-nums">{(r.mispredictionRate * 100).toFixed(2)}%</td>
              <td className="px-4 py-2 text-right tabular-nums">{r.mpki.toFixed(3)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
