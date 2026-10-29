import React, { useState } from 'react'
import { ResponsiveContainer, BarChart, Bar, LineChart, Line, ComposedChart, XAxis, YAxis, Tooltip, Legend, CartesianGrid, ReferenceLine } from 'recharts'
import { useApi, fetchJson, money, num, pct } from './api.js'
import { Badge, Grid, Loading, Card, Progress, MessageBox } from './ui.jsx'

// Culorile mai deschise (pastelate) pentru Customer Segments
const COLORS = ['#60a5fa', '#4ade80', '#fbbf24', '#f87171', '#a78bfa']

export function SegmentsTab({ f }) {
  const [by, setBy] = useState('gender')
  const st = useApi('/segments', { store: f.store, by })
  const d = st.data
  return (<Card title="Customer segments and category preferences" right={
    <label className="inline">Segment by <select value={by} onChange={e => setBy(e.target.value)}><option value="gender">Gender</option><option value="age">Age</option><option value="education">Education</option></select></label>}>
    <Loading state={st} />
    {d && <><ResponsiveContainer width="100%" height={320}><BarChart data={d.chart}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="segment" /><YAxis tickFormatter={v => `${Math.round(v / 1000)}k`} /><Tooltip formatter={v => money(v)} /><Legend />
      {d.categories.map((c, i) => <Bar key={c} dataKey={c} stackId="a" fill={COLORS[i % 5]} />)}</BarChart></ResponsiveContainer>
      <h4>Segment ↔ subcategory associations (lift = share in segment / average share)</h4>
      <Grid rowStatus rows={d.lifts.slice(0, 20).map((r, i) => ({ ...r, id: i }))} columns={[{ key: 'segment', label: 'Segment' }, { key: 'subcategory', label: 'Subcategory' },
        { key: 'sharePct', label: 'In segment', align: 'r', render: v => pct(v) }, { key: 'overallPct', label: 'Average', align: 'r', render: v => pct(v) },
        { key: 'lift', label: 'Lift', align: 'r', render: v => `×${v}` }, { key: 'status', label: 'Interpretation', render: (_, r) => <Badge s={r.status} /> }]} />
      <div className="note">Example: lift ×2 means the segment buys twice as much of that subcategory as the average customer.</div></>}
  </Card>)
}

export function PeaksTab({ f }) {
  const pk = useApi('/peaks', f), ev = useApi('/events', f)
  return (<>
    <Card title="Sales peaks and declines in the selected period"><Loading state={pk} />
      {pk.data && <div className="two"><Grid rowStatus rows={pk.data.months.map(m => ({ ...m, id: m.ym }))} columns={[{ key: 'ym', label: 'Month' }, { key: 'revenue', label: 'Revenue', align: 'r', render: money },
        { key: 'vsAvgPct', label: 'vs. average', align: 'r', render: v => pct(v, true) }, { key: 'status', label: 'Type', render: (_, r) => <Badge s={r.status} /> }]} />
        <div><h4>Monthly seasonality index (1.00 = average; excluding 2020)</h4><ResponsiveContainer width="100%" height={260}><BarChart data={pk.data.seasonality}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="month" /><YAxis domain={[0.6, 1.5]} />
          <Tooltip /><ReferenceLine y={1} stroke="#111827" strokeDasharray="4 4" />
          <Bar dataKey="index" name="Index" fill="#059669" />
          </BarChart></ResponsiveContainer>
          <Grid rowStatus rows={pk.data.seasonality.map(s => ({ ...s, id: s.month }))} columns={[{ key: 'month', label: 'Month' }, { key: 'index', label: 'Index', align: 'r' }, { key: 'advice', label: 'Recommendation' }]} /></div></div>}
    </Card>
    <Card title="Impact of external events on daily sales"><Loading state={ev} />
      {ev.data && <Grid rowStatus rows={ev.data.map((e, i) => ({ ...e, id: i }))} columns={[{ key: 'name', label: 'Event' }, { key: 'type', label: 'Type' }, { key: 'start', label: 'Start' },
        { key: 'avgDaily', label: 'Revenue/day', align: 'r', render: money }, { key: 'baselineDaily', label: 'Before (28 days)', align: 'r', render: money }, { key: 'liftPct', label: 'Effect', align: 'r', render: v => pct(v, true) },
        { key: 'status', label: 'Assessment', render: (_, r) => <Badge s={r.status} /> }]} />}
    </Card></>)
}

export function PromoTab({ f }) {
  const [sub, setSub] = useState('ALL'), [disc, setDisc] = useState(15)
  const hist = useApi('/promo/history'), sim = useApi('/promo/simulate', { store: f.store, channel: f.channel, gender: f.gender, ageGroup: f.ageGroup, education: f.education, sub, discount: disc })
  const subs = [...new Map((hist.data || []).filter(h => h.subId).map(h => [h.subId, h.subcategory])).entries()]
  const s = sim.data
  return (<>
    <Card title="Simulation: effect of a price reduction (last 12 months as baseline)" right={
      <span className="inline"><select value={sub} onChange={e => setSub(e.target.value)}><option value="ALL">All subcategories</option>{subs.map(([id, n]) => <option key={id} value={id}>{n}</option>)}</select>
        <input type="range" min="0" max="40" step="1" value={disc} onChange={e => setDisc(+e.target.value)} /> <b>{disc}%</b></span>}>
      <Loading state={sim} />
      {s && <><div className="kpis">
        <div className="kpi"><div className="kl">Estimated elasticity</div><div className="kv">{s.elasticity}</div><div className="kd">from previous promotions</div></div>
        <div className="kpi"><div className="kl">Revenue</div><div className="kv">{money(s.sim.revenue)}</div><div className={`kd ${s.sim.revenue >= s.base.revenue ? 'up' : 'down'}`}>vs {money(s.base.revenue)}</div></div>
        <div className="kpi"><div className="kl">Gross margin</div><div className="kv">{money(s.sim.margin)}</div><div className={`kd ${s.sim.margin >= s.base.margin ? 'up' : 'down'}`}>vs {money(s.base.margin)}</div></div>
        <div className="kpi"><div className="kl">Units</div><div className="kv">{num(s.sim.units)}</div><div className="kd">vs {num(s.base.units)}</div></div>
        <div className="kpi"><div className="kl">Verdict</div><div className="kv"><span className={`badge ${s.status}`}>{s.status === 'green' ? 'Recommended' : 'Not recommended'}</span></div><div className="kd">{s.verdict}</div></div></div>
        <ResponsiveContainer width="100%" height={280}><LineChart data={s.curve}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="discount" unit="%" /><YAxis tickFormatter={v => `${Math.round(v / 1000)}k`} /><Tooltip formatter={v => money(v)} /><Legend />
          <Line dataKey="revenue" name="Revenue" stroke="#f59e0b" strokeWidth={2} />
          <Line dataKey="margin" name="Margin" stroke="#16a34a" strokeWidth={2} />
          <ReferenceLine x={disc} stroke="#dc2626" strokeDasharray="4 4" /></LineChart></ResponsiveContainer></>}
    </Card>
    <Card title="Previous promotions and measured impact (adjusted for overall demand growth)"><Loading state={hist} />
      {hist.data && <Grid rowStatus rows={hist.data.map((h, i) => ({ ...h, id: i }))} columns={[{ key: 'name', label: 'Promotion' }, { key: 'subcategory', label: 'Subcategory' }, { key: 'start', label: 'Month' },
        { key: 'discountPct', label: 'Discount', align: 'r', render: v => `${v}%` }, { key: 'liftPct', label: 'Unit increase', align: 'r', render: v => pct(v, true) }, { key: 'elasticity', label: 'Elasticity', align: 'r' },
        { key: 'status', label: 'Efficiency', render: (_, r) => <Badge s={r.status} /> }]} />}
    </Card></>)
}

export function SupplyTab({ f }) {
  const [h, setH] = useState(3)
  const st = useApi('/supply', { ...f, horizon: h }), d = st.data
  return (<Card title="Inventory needs by subcategory" right={
    <label className="inline">Period <select value={h} onChange={e => setH(+e.target.value)}>{[1, 3, 6, 12].map(x => <option key={x} value={x}>{x} months</option>)}</select></label>}>
    <Loading state={st} />
    {d && <><div className="two"><Grid rowStatus rows={d.rows.map(r => ({ ...r, id: r.name }))} columns={[{ key: 'name', label: 'Subcategory' }, { key: 'forecastUnits', label: 'Estimated demand', align: 'r', render: num },
      { key: 'orderUnits', label: `To order (+${d.safetyStockPct}% safety stock)`, align: 'r', render: num }, { key: 'orderValue', label: 'Value (cost)', align: 'r', render: money },
      { key: 'lastYearUnits', label: 'Last year', align: 'r', render: num }, { key: 'varPct', label: 'Variation', align: 'r', render: v => pct(v, true) }, { key: 'status', label: 'Trend', render: (_, r) => <Badge s={r.status} /> }]} />
      <div><h4>Monthly revenue forecast</h4><ResponsiveContainer width="100%" height={300}><ComposedChart data={d.forecast}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="ym" /><YAxis tickFormatter={v => `${Math.round(v / 1000)}k`} /><Tooltip formatter={v => money(v)} /><Legend />
        <Line dataKey="high" name="Upper bound" stroke="#9ca3af" strokeDasharray="4 4" /><Line dataKey="forecast" name="Forecast" stroke="#f59e0b" strokeWidth={3} /><Line dataKey="low" name="Lower bound" stroke="#9ca3af" strokeDasharray="4 4" /></ComposedChart></ResponsiveContainer></div></div>
      <div className="note">Method: average level of the last 12 months × seasonality index × annual growth factor (capped at 0.7–1.4).</div></>}
  </Card>)
}

const ZONES = [{ id: 'promo', label: 'Promote', cls: 'green' }, { id: 'restock', label: 'Restock', cls: 'yellow' }, { id: 'retire', label: 'Retire / reduce', cls: 'red' }]

export function DecisionTab({ f, notify }) {
  const [w, setW] = useState({ wg: 35, wm: 35, ws: 15, wmo: 15 })
  const [progress, setProgress] = useState(null), [rows, setRows] = useState(null), [plan, setPlan] = useState({ promo: [], restock: [], retire: [] })
  const [pending, setPending] = useState(null), [stamp, setStamp] = useState(null)
  const compute = async () => {
    try {
      setProgress({ v: 5, l: 'Preparing data' }); await fetchJson('/alerts', f)
      setProgress({ v: 35, l: 'Calculating stock requirements' }); await fetchJson('/supply', { ...f, horizon: 3 })
      setProgress({ v: 70, l: 'Evaluating criteria and scores' }); const d = await fetchJson('/decision', { ...f, ...w })
      setProgress({ v: 100, l: 'Complete' }); setRows(d.rows); setStamp(new Date().toLocaleString('en-US')); setPlan({ promo: [], restock: [], retire: [] })
      notify('success', 'Recommendation calculated', `${d.rows.length} subcategories evaluated.`)
      setTimeout(() => setProgress(null), 800)
    } catch (e) { setProgress(null); notify('danger', 'Calculation error', e.message) }
  }
  const drop = (zone, e) => { e.preventDefault(); const id = e.dataTransfer.getData('text'); const r = rows.find(x => x.id === id); if (r) setPending({ zone, r }) }
  const confirm = () => { const { zone, r } = pending; setPlan(p => ({ promo: p.promo.filter(x => x.id !== r.id), restock: p.restock.filter(x => x.id !== r.id), retire: p.retire.filter(x => x.id !== r.id), [zone]: [...p[zone].filter(x => x.id !== r.id), r] })); setPending(null) }
  const Slider = ({ k, label }) => <label className="slider">{label}: <b>{w[k]}</b><input type="range" min="0" max="100" value={w[k]} onChange={e => setW({ ...w, [k]: +e.target.value })} /></label>
  return (<>
    <Card title="Multi-criteria decision: what we should promote, restock or retire">
      <div className="weights"><Slider k="wg" label="Growth (12 months)" /><Slider k="wm" label="Margin" /><Slider k="ws" label="Share of sales" /><Slider k="wmo" label="Momentum (last 3 months)" />
        <button onClick={compute} disabled={!!progress}>Calculate recommendation</button></div>
      {progress && <Progress value={progress.v} label={progress.l} />}
      {rows && <><Grid rowStatus rows={rows} columns={[{ key: 'name', label: 'Subcategory' }, { key: 'category', label: 'Category' }, { key: 'revenue', label: 'Revenue 12 months', align: 'r', render: money },
        { key: 'growthPct', label: 'Growth', align: 'r', render: v => pct(v, true) }, { key: 'marginPct', label: 'Margin', align: 'r', render: v => pct(v) }, { key: 'momentumPct', label: 'Momentum', align: 'r', render: v => pct(v, true) },
        { key: 'score', label: 'Score', align: 'r' }, { key: 'status', label: 'Assessment', render: (_, r) => <Badge s={r.status} /> }, { key: 'action', label: 'Suggested action' }, { key: 'rationale', label: 'Reason' }]} />
        <div className="note">Calculated at {stamp}. Score = weighted average of normalized criteria 0–100 (green ≥ 60, yellow ≥ 40, red &lt; 40).</div></>}
    </Card>
    {rows && <Card title="Decision plan — drag subcategories into the desired zone"><div className="chips">{rows.map(r => <span key={r.id} draggable onDragStart={e => e.dataTransfer.setData('text', r.id)} className={`chip ${r.status}`}>{r.name}</span>)}</div>
      <div className="zones">{ZONES.map(z => <div key={z.id} className={`zone ${z.cls}`} onDragOver={e => e.preventDefault()} onDrop={e => drop(z.id, e)}><h4>{z.label} ({plan[z.id].length})</h4>
        {plan[z.id].map(r => <div key={r.id} className="planitem">{r.name} <small>score {r.score}</small></div>)}</div>)}</div>
      <div className="note">The plan is only a UI decision simulation — the application does not modify data (it is not transactional).</div></Card>}
    {pending && <MessageBox title="Confirm decision" text={`Move “${pending.r.name}” (score ${pending.r.score}, suggestion: ${pending.r.action}) to “${ZONES.find(z => z.id === pending.zone).label}”?`} onYes={confirm} onNo={() => setPending(null)} />}
  </>)
}