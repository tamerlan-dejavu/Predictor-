import axios from 'axios'

const API = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

export const runPredictor = (req) => API.post('/run', req)
export const comparePredictos = (req) => API.post('/compare', req)
export const getExperiment = (params) => API.get('/experiment', { params })
export const getHealth = () => API.get('/health')

export default API
