import axios from 'axios'

/**
 * Базовый URL бэкенда без завершающего слэша.
 * Локально: пусто → запросы идут на тот же origin, Vite проксирует /api на :8080.
 * Продакшен (Vercel): задайте VITE_API_BASE_URL=https://your-api.onrender.com
 */
const backendOrigin = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

const API = axios.create({
  baseURL: backendOrigin ? `${backendOrigin}/api` : '/api',
  headers: { 'Content-Type': 'application/json' },
})

export const runPredictor = (req) => API.post('/run', req)
export const comparePredictors = (req) => API.post('/compare', req)
/** @deprecated use {@link comparePredictors} */
export const comparePredictos = comparePredictors
export const getExperiment = (params) => API.get('/experiment', { params })
export const getHealth = () => API.get('/health')

export default API
