import { useState } from 'react'
import PredictorForm from '../components/PredictorForm.jsx'
import ComparisonChart from '../components/ComparisonChart.jsx'
import ResultsTable from '../components/ResultsTable.jsx'
import LoadingSpinner from '../components/LoadingSpinner.jsx'
import { comparePredictos } from '../api/predictorApi.js'

export default function DashboardPage() {
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (req) => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await comparePredictos(req)
      setResults(data)
    } catch (e) {
      setError(e?.response?.data?.error || e.message || 'Request failed')
      setResults([])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-900">Compare predictors</h2>
        <p className="text-slate-600 text-sm mt-1">
          Run multiple branch predictors against the same trace and compare misprediction rates.
        </p>
      </div>

      <div className="grid lg:grid-cols-2 gap-6">
        <PredictorForm mode="compare" onSubmit={handleSubmit} loading={loading} />

        <div className="space-y-4">
          {loading && <LoadingSpinner label="Running predictors…" />}
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-800 rounded-md px-4 py-3 text-sm">
              {error}
            </div>
          )}
          {!loading && !error && results.length === 0 && (
            <div className="bg-white border border-dashed border-slate-300 rounded-lg p-8 text-center text-slate-500 text-sm">
              Submit the form to see comparison results.
            </div>
          )}
          {results.length > 0 && <ComparisonChart data={results} />}
        </div>
      </div>

      {results.length > 0 && <ResultsTable rows={results} />}
    </div>
  )
}
