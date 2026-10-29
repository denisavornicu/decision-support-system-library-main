import { useEffect, useState } from 'react'

export const STATUS = { green: 'Satisfactory', yellow: 'Somewhat satisfactory', red: 'Unsatisfactory' }
export const money = v => v == null ? '–' : Number(v).toLocaleString('en-CA', { style: 'currency', currency: 'CAD', maximumFractionDigits: 0 })
export const num = v => v == null ? '–' : Number(v).toLocaleString('en-CA', { maximumFractionDigits: 0 })
export const pct = (v, sign = false) => v == null ? '–' : `${sign && v > 0 ? '+' : ''}${Number(v).toFixed(1)}%`

/** Construieste query string din obiect, ignorand valorile goale / ALL. */
export const qs = o => Object.entries(o).filter(([, v]) => v !== '' && v != null && v !== 'ALL').map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')

/** Hook de date: refetch automat la orice schimbare de parametri. Emite timestamp-ul ultimei actualizari. */
export function useApi(path, params = {}) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const key = path + '?' + qs(params)
  useEffect(() => {
    let live = true
    setState(s => ({ ...s, loading: true, error: null }))
    fetch('/api' + key)
      .then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() })
      .then(d => { if (live) { setState({ data: d, loading: false, error: null }); window.dispatchEvent(new CustomEvent('sad:stamp')) } })
      .catch(e => live && setState({ data: null, loading: false, error: e.message }))
    return () => { live = false }
  }, [key])
  return state
}

export async function fetchJson(path, params = {}) {
  const r = await fetch(`/api${path}?${qs(params)}`)
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}
