import { NavLink, Route, Routes } from 'react-router-dom'
import DashboardPage from './pages/DashboardPage.jsx'
import ExperimentPage from './pages/ExperimentPage.jsx'

const navLinkClass = ({ isActive }) =>
  `px-4 py-2 rounded-md text-sm font-medium transition-colors ${
    isActive ? 'bg-indigo-600 text-white' : 'text-slate-700 hover:bg-slate-200'
  }`

export default function App() {
  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-slate-200 bg-white">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
          <h1 className="text-xl font-bold text-slate-900">Branch Predictor Lab</h1>
          <nav className="flex gap-2">
            <NavLink to="/" end className={navLinkClass}>Dashboard</NavLink>
            <NavLink to="/experiment" className={navLinkClass}>Experiment</NavLink>
          </nav>
        </div>
      </header>

      <main className="flex-1 max-w-6xl w-full mx-auto px-6 py-8">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/experiment" element={<ExperimentPage />} />
        </Routes>
      </main>
    </div>
  )
}
