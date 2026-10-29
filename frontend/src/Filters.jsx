import React, { useEffect, useState } from 'react'
import { useApi } from './api.js'

const shift = (ym, n) => { const [y, m] = ym.split('-').map(Number); const d = new Date(y, m - 1 + n, 1); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}` }
const diff = (a, b) => { const [y1, m1] = a.split('-').map(Number), [y2, m2] = b.split('-').map(Number); return (y2 - y1) * 12 + m2 - m1 }

/** Bara de filtre globala. Orice schimbare => `onChange` (debounce 300 ms) => toate grid-urile/graficele se reactualizeaza. */
export default function Filters({ onChange }) {
  const { data: opt } = useApi('/filters')
  const [f, setF] = useState({ from: '', to: '', refMode: 'prev', refFrom: '', refTo: '', store: 'ALL', channel: 'ALL', category: 'ALL', gender: 'ALL', ageGroup: 'ALL', education: 'ALL' })
  const set = (k, v) => setF(s => ({ ...s, [k]: v }))
  useEffect(() => { if (opt && !f.to) setF(s => ({ ...s, to: opt.maxYm, from: shift(opt.maxYm, -11) })) }, [opt])
  useEffect(() => {
    if (!f.from || !f.to) return
    const t = setTimeout(() => {
      let refFrom = '', refTo = ''
      if (f.refMode === 'yoy') { refFrom = shift(f.from, -12); refTo = shift(f.to, -12) }
      if (f.refMode === 'manual') { refFrom = f.refFrom; refTo = f.refTo }
      onChange({ from: f.from, to: f.to, refFrom, refTo, store: f.store, channel: f.channel, category: f.category, gender: f.gender, ageGroup: f.ageGroup, education: f.education })
    }, 300)
    return () => clearTimeout(t)
  }, [f])
  if (!opt) return <div className="filters">Loading filters…</div>
  const preset = (months) => setF(s => ({ ...s, to: opt.maxYm, from: shift(opt.maxYm, -(months - 1)) }))
  const year = y => setF(s => ({ ...s, from: `${y}-01`, to: `${y}-12` }))
  const Sel = ({ k, label, items, idKey = 'id' }) => (
    <label>{label}<select value={f[k]} onChange={e => set(k, e.target.value)}><option value="ALL">All</option>
      {items.map(i => <option key={i[idKey]} value={i[idKey]}>{i.name || i[idKey]}</option>)}</select></label>)
  const invalid = f.from && f.to && diff(f.from, f.to) < 0
  return (
    <div className="filters">
      <label>From<input type="month" min={opt.minYm} max={opt.maxYm} value={f.from} onChange={e => set('from', e.target.value)} /></label>
      <label>To<input type="month" min={opt.minYm} max={opt.maxYm} value={f.to} onChange={e => set('to', e.target.value)} /></label>
      <div className="presets"><button className="ghost" onClick={() => preset(12)}>12 months</button><button className="ghost" onClick={() => preset(3)}>3 months</button>
        {[2022, 2023, 2024].map(y => <button className="ghost" key={y} onClick={() => year(y)}>{y}</button>)}</div>
      <label>Reference period
        <select value={f.refMode} onChange={e => set('refMode', e.target.value)}>
          <option value="prev">Previous period</option><option value="yoy">Same period last year</option><option value="manual">Manual selection</option></select></label>
      {f.refMode === 'manual' && <><label>Ref. from<input type="month" value={f.refFrom} onChange={e => set('refFrom', e.target.value)} /></label>
        <label>Ref. to<input type="month" value={f.refTo} onChange={e => set('refTo', e.target.value)} /></label></>}
      <Sel k="store" label="Store" items={opt.stores} />
      <Sel k="channel" label="Channel" items={opt.channels} />
      <Sel k="category" label="Category" items={opt.categories} />
      <Sel k="gender" label="Gender" items={opt.genders} />
      <Sel k="ageGroup" label="Age" items={opt.ageGroups} />
      <Sel k="education" label="Education" items={opt.educations} />
      {invalid && <span className="badge red">Invalid period: “From” is after “To”</span>}
    </div>
  )
}
