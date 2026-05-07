export default function LoadingSpinner({ label = 'Loading…' }) {
  return (
    <div className="flex items-center gap-3 text-slate-600">
      <span className="inline-block w-5 h-5 rounded-full border-2 border-slate-300 border-t-indigo-600 animate-spin" />
      <span className="text-sm">{label}</span>
    </div>
  )
}
