import React, { useCallback, useEffect, useState } from 'react'
import Filters from './Filters.jsx'
import { Overview, DrillTab, GeoTab } from './tabs1.jsx'
import { SegmentsTab, PeaksTab, PromoTab, SupplyTab, DecisionTab } from './tabs2.jsx'
import { useApi } from './api.js'

const TABS = [['overview', 'Overview', Overview], ['drill', 'Sales (drill-down)', DrillTab], ['geo', 'Customer geography', GeoTab], ['seg', 'Customer segments', SegmentsTab],
  ['peaks', 'Peaks & events', PeaksTab], ['promo', 'Promotions', PromoTab], ['supply', 'Supply', SupplyTab], ['decision', 'Decision', DecisionTab]]

export default function App() {
  const [f, setF] = useState(null), [tab, setTab] = useState('overview'), [stamp, setStamp] = useState(null), [toasts, setToasts] = useState([]), [showAlerts, setShowAlerts] = useState(false)
  useEffect(() => { const h = () => setStamp(new Date()); window.addEventListener('sad:stamp', h); return () => window.removeEventListener('sad:stamp', h) }, [])
  const notify = useCallback((level, title, message) => {
    const id = Math.random(); setToasts(t => [...t, { id, level, title, message }]); setTimeout(() => setToasts(t => t.filter(x => x.id !== id)), 5000)
  }, [])
  const alerts = useApi('/alerts', f || {})
  const [shown, setShown] = useState(false)
  useEffect(() => { if (alerts.data && !shown) { setShown(true); alerts.data.filter(a => a.level !== 'success').slice(0, 3).forEach(a => notify(a.level, a.title, a.message)) } }, [alerts.data])
  const Active = TABS.find(t => t[0] === tab)[2]
  return (
    <div className="app">
      <header><div><h1>Library — Northern Lights Book Co.</h1><span>Decision support system · Calgary, AB · 2019–2024</span></div>
        <button className="bell" onClick={() => setShowAlerts(s => !s)}>Alerts <b>{alerts.data?.length ?? 0}</b></button></header>
      {showAlerts && alerts.data && <div className="alertpanel">{alerts.data.map((a, i) => <div key={i} className={`alert ${a.level}`}><b>{a.title}</b> — {a.message}</div>)}</div>}
      <Filters onChange={setF} />
      <nav>{TABS.map(([id, label]) => <button key={id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>{label}</button>)}</nav>
      <main>{f ? <Active f={f} notify={notify} /> : <div className="note">Initializing…</div>}</main>
      <footer>Last update: {stamp ? stamp.toLocaleString('en-US') : '–'} · Values in CAD, excl. GST · Synthetic data fully populated through documented generation</footer>
      <div className="toasts">{toasts.map(t => <div key={t.id} className={`toast ${t.level}`}><b>{t.title}</b><div>{t.message}</div></div>)}</div>
    </div>
  )
}
