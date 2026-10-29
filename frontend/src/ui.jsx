import React, { useState, useMemo } from 'react'
import { STATUS } from './api.js'

export const Badge = ({ s }) => <span className={`badge ${s}`}>{STATUS[s]}</span>

export function Loading({ state }) {
  if (state.loading) return <div className="note">Loading…</div>
  if (state.error) return <div className="note err">Error: {state.error}. Is the backend running on port 8080?</div>
  return null
}

/** Grid sortabil, cu celule colorate prin `cell` si rand clicabil. columns: [{key,label,render?,align?}] */
export function Grid({ columns, rows, onRowClick, rowStatus, empty = 'No data available.' }) {
  const [sort, setSort] = useState({ key: null, dir: 1 })
  const sorted = useMemo(() => {
    if (!sort.key) return rows
    return [...rows].sort((a, b) => (a[sort.key] > b[sort.key] ? 1 : a[sort.key] < b[sort.key] ? -1 : 0) * sort.dir)
  }, [rows, sort])
  if (!rows.length) return <div className="note">{empty}</div>
  return (
    <div className="gridwrap"><table className="grid">
      <thead><tr>{columns.map(c => (
        <th key={c.key} className={c.align} onClick={() => setSort(s => ({ key: c.key, dir: s.key === c.key ? -s.dir : 1 }))}>
          {c.label}{sort.key === c.key ? (sort.dir > 0 ? ' ▲' : ' ▼') : ''}</th>))}</tr></thead>
      <tbody>{sorted.map((r, i) => (
        <tr key={r.id ?? i} className={`${rowStatus ? 'row-' + r.status : ''} ${onRowClick && r.hasChildren !== false ? 'click' : ''}`}
            onClick={() => onRowClick && r.hasChildren !== false && onRowClick(r)}>
          {columns.map(c => <td key={c.key} className={c.align}>{c.render ? c.render(r[c.key], r) : r[c.key]}</td>)}
        </tr>))}</tbody>
    </table></div>
  )
}

export function Progress({ value, label }) {
  return <div className="progress"><div className="bar" style={{ width: `${value}%` }} /><span>{label} ({Math.round(value)}%)</span></div>
}

/** Message box modal. */
export function MessageBox({ title, text, onYes, onNo }) {
  return (
    <div className="overlay"><div className="modal"><h3>{title}</h3><p>{text}</p>
      <div className="actions"><button className="ghost" onClick={onNo}>Cancel</button><button onClick={onYes}>Confirm</button></div></div></div>
  )
}

export const Card = ({ title, children, right }) => (
  <section className="card"><div className="cardhead"><h3>{title}</h3>{right}</div>{children}</section>
)
