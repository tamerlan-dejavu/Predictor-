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
      const msg = e?.response?.data?.error || e?.message || 'Failed to execute request'
      setError(msg)
      setResults([])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-5">
      <section>
        <h2 className="text-2xl font-bold text-white">Compare Predictors</h2>
        <p className="text-gray-400 text-sm mt-1">
          Select predictors and a trace — compare misprediction rate and MPKI.
        </p>
      </section>

      <div className="grid grid-cols-1 lg:grid-cols-10 gap-5">
        {/* Left: form (30%) */}
        <aside className="lg:col-span-3">
          <PredictorForm onSubmit={handleSubmit} loading={loading} />
        </aside>

        {/* Right: chart + table (70%) */}
        <section className="lg:col-span-7 space-y-5">
          {error && (
            <div className="bg-red-950/60 border border-red-700 text-red-200 rounded-md px-4 py-3 text-sm flex items-start gap-2">
              <span className="font-semibold text-red-300">Error:</span>
              <span>{error}</span>
            </div>
          )}

          {loading && (
            <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
              <LoadingSpinner label="Running predictors…" />
            </div>
          )}

          {!loading && !error && results.length === 0 && (
            <div className="bg-gray-800 border border-dashed border-gray-700 rounded-lg p-12 text-center text-gray-400 text-sm">
              Fill the form and click "Run Comparison" to see results.
            </div>
          )}

          {!loading && results.length > 0 && (
            <>
              <ComparisonChart data={results} />
              <ResultsTable rows={results} />
            </>
          )}
        </section>
      </div>
    </div>
  )
}
