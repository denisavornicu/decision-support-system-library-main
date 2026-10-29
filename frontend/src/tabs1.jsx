import React, { useState } from 'react'
import { ResponsiveContainer, ComposedChart, BarChart, Bar, Line, Area, XAxis, YAxis, Tooltip, Legend, CartesianGrid, ReferenceLine } from 'recharts'
import { useApi, money, num, pct } from './api.js'
import { Badge, Grid, Loading, Card } from './ui.jsx'

const Kpi = ({ label, value, variation, fmt }) => (
  <div className="kpi"><div className="kl">{label}</div><div className="kv">{fmt(value)}</div>
    <div className={`kd ${variation >= 0 ? 'up' : 'down'}`}>{variation >= 0 ? '▲' : '▼'} {pct(variation, true)} vs reference</div></div>)

export function Overview({ f }) {
  const [horizon, setHorizon] = useState(6)
  const ov = useApi('/overview', f), tr = useApi('/trend', { ...f, horizon })
  const pts = (tr.data?.points || []).map(p => ({ ...p, band: p.low != null ? [p.low, p.high] : null }))
  const o = ov.data
  return (<>
    <Loading state={ov} />
    {o && <div className="kpis">
      <Kpi label="Net revenue" value={o.current.revenue} variation={o.variation.revenue} fmt={money} />
      <Kpi label="Units sold" value={o.current.units} variation={o.variation.units} fmt={num} />
      <Kpi label="Gross margin" value={o.current.margin} variation={o.variation.margin} fmt={money} />
      <Kpi label="Margin %" value={o.current.marginPct} variation={o.variation.marginPct} fmt={v => pct(v)} />
      <Kpi label="Transactions" value={o.current.transactions} variation={o.variation.transactions} fmt={num} />
      <Kpi label="Average basket" value={o.current.avgBasket} variation={o.variation.avgBasket} fmt={v => money(v)} />
    </div>}
    <Card title="Monthly evolution, trend & forecast" right={
      <label className="inline">Forecast horizon <select value={horizon} onChange={e => setHorizon(+e.target.value)}>{[3, 6, 12].map(h => <option key={h} value={h}>{h} months</option>)}</select></label>}>
      <Loading state={tr} />
      <ResponsiveContainer width="100%" height={360}><ComposedChart data={pts}>
        <CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="ym" /><YAxis tickFormatter={v => `${Math.round(v / 1000)}k`} /><Tooltip formatter={v => Array.isArray(v) ? `${money(v[0])} – ${money(v[1])}` : money(v)} /><Legend />
        <Area dataKey="band" name="80% confidence interval" fill="#f59e0b" stroke="none" fillOpacity={0.2} />
        <Bar dataKey="revenue" name="Revenue" fill="#0f766e" barSize={14} />
        <Line dataKey="ma3" name="3-month moving average" stroke="#d97706" dot={false} strokeWidth={2} />
        <Line dataKey="trend" name="Linear trend" stroke="#111827" strokeDasharray="6 4" dot={false} />
        <Line dataKey="ref" name="Reference period" stroke="#9ca3af" dot={false} strokeWidth={2} />
        <Line dataKey="forecast" name="Forecast" stroke="#f59e0b" strokeWidth={3} strokeDasharray="4 3" />
      </ComposedChart></ResponsiveContainer>
      {tr.data && <div className="note">Trend: {tr.data.slopePerMonth >= 0 ? '+' : ''}{money(tr.data.slopePerMonth)} per month · monthly average {money(tr.data.avg)}. Forecast appears when the period ends in the latest month in the data.</div>}
    </Card>
  </>)
}

/** Drill-down generic: dynamically moves through a hierarchy (category>subcategory>title or province>region>neighborhood). */
function Hierarchy({ f, endpoint, levels, columns, chartKey, chartRef, extra }) {
  const [path, setPath] = useState([])
  const level = levels[path.length], parent = path.length ? path[path.length - 1].id : undefined
  const st = useApi(endpoint, { ...f, level, parent, ...extra })
  const rows = st.data?.rows || []
  return (<>
    <div className="crumbs">
      <button className="link" onClick={() => setPath([])}>All</button>
      {path.map((p, i) => <React.Fragment key={i}> › <button className="link" onClick={() => setPath(path.slice(0, i + 1))}>{p.name}</button></React.Fragment>)}
    </div>
    <Loading state={st} />
    <div className="two">
      <Grid columns={columns} rows={rows} rowStatus onRowClick={r => setPath([...path, { id: r.id, name: r.name }])} />
      <ResponsiveContainer width="100%" height={Math.max(260, rows.length * 32)}><BarChart data={rows.slice(0, 15)} layout="vertical" margin={{ left: 40 }}>
        <CartesianGrid strokeDasharray="3 3" /><XAxis type="number" tickFormatter={v => `${Math.round(v / 1000)}k`} /><YAxis type="category" dataKey="name" width={130} />
        <Tooltip formatter={v => money(v)} /><Legend />
        <Bar dataKey={chartKey} name="Current period" fill="#059669" />
        {chartRef && <Bar dataKey={chartRef} name="Reference" fill="#d97706" />}
      </BarChart></ResponsiveContainer>
    </div>
    <div className="note">Click a row to drill down for details. Colors: green = satisfactory, yellow = somewhat satisfactory, red = unsatisfactory.</div>
  </>)
}

export function DrillTab({ f }) {
  const [good, setGood] = useState(3), [bad, setBad] = useState(-5)
  const cols = [
    { key: 'name', label: 'Name' }, { key: 'revenue', label: 'Revenue', align: 'r', render: money }, { key: 'sharePct', label: 'Share', align: 'r', render: v => pct(v) },
    { key: 'units', label: 'Units', align: 'r', render: num }, { key: 'marginPct', label: 'Margin %', align: 'r', render: v => pct(v) },
    { key: 'refRevenue', label: 'Reference', align: 'r', render: money }, { key: 'varPct', label: 'Variation', align: 'r', render: v => pct(v, true) },
    { key: 'status', label: 'Assessment', render: (_, r) => <Badge s={r.status} /> }]
  return (<Card title="Sales: category → subcategory → title" right={
    <span className="inline">Satisfactory ≥ <input type="number" value={good} onChange={e => setGood(+e.target.value)} />% · Unsatisfactory &lt; <input type="number" value={bad} onChange={e => setBad(+e.target.value)} />%</span>}>
    <Hierarchy f={f} endpoint="/drill" levels={['category', 'subcategory', 'title']} columns={cols} chartKey="revenue" chartRef="refRevenue" extra={{ good, bad }} /></Card>)
}

export function GeoTab({ f }) {
  const cols = [{ key: 'name', label: 'Area' }, { key: 'customers', label: 'Customers', align: 'r', render: num }, { key: 'spent', label: 'Total spend', align: 'r', render: money },
    { key: 'avgSpent', label: 'Average/customer', align: 'r', render: money }, { key: 'rating', label: 'Rating', align: 'r' }, { key: 'escPct', label: 'Complaints %', align: 'r', render: v => pct(v) },
    { key: 'status', label: 'Satisfaction', render: (_, r) => <Badge s={r.status} /> }]
  return (<Card title="Customers: province → region → neighborhood / locality">
    <Hierarchy f={f} endpoint="/geo" levels={['province', 'region', 'neighborhood']} columns={cols} chartKey="spent" />
    <div className="note">Cumulative values per customer (geographic data are at the customer level). This period does not apply here.</div></Card>)
}