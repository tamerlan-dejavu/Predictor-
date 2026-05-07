import { NavLink, Route, Routes } from 'react-router-dom'
import DashboardPage from './pages/DashboardPage.jsx'
import ExperimentPage from './pages/ExperimentPage.jsx'

const navLinkClass = ({ isActive }) =>
  `px-4 py-2 rounded-md text-sm font-medium transition-colors ${
    isActive ? 'bg-blue-600 text-white' : 'text-gray-300 hover:bg-gray-800'
  }`

export default function App() {
  return (
    <div className="min-h-screen flex flex-col bg-gray-900 text-gray-100">
      <header className="border-b border-gray-800 bg-gray-900">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <h1 className="text-xl font-bold text-white">
            Branch Predictor Lab <span className="text-gray-400 font-normal">— Команда Девчонки</span>
          </h1>
          <nav className="flex gap-2">
            <NavLink to="/" end className={navLinkClass}>Dashboard</NavLink>
            <NavLink to="/experiment" className={navLinkClass}>Experiment</NavLink>
          </nav>
        </div>
      </header>

      <main className="flex-1 max-w-7xl w-full mx-auto px-6 py-6">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/experiment" element={<ExperimentPage />} />
        </Routes>
      </main>
    </div>
  )
}
