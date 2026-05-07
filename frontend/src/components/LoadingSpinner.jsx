export default function LoadingSpinner({ label = 'Загрузка…' }) {
  return (
    <div className="flex items-center gap-3 text-gray-300">
      <span className="inline-block w-5 h-5 rounded-full border-2 border-gray-600 border-t-blue-500 animate-spin" />
      <span className="text-sm">{label}</span>
    </div>
  )
}
