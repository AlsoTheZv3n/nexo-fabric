import { useQuery } from '@apollo/client'
import { GET_ALL_OBJECT_TYPES, GET_ACTION_LOG } from '@/api/graphql/queries'
import {
  Boxes, Database, GitFork, Activity, Cpu, Plug, ArrowRight,
  Rocket, Plus
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import type { ObjectTypeSchema, ActionLogEntry } from '@/types'

function KpiCard({
  icon: Icon,
  label,
  value,
  color = 'accent',
  href,
}: {
  icon: React.ElementType
  label: string
  value: string | number
  color?: string
  href?: string
}) {
  const navigate = useNavigate()
  const colorMap: Record<string, string> = {
    accent: 'bg-accent/15 text-accent',
    tertiary: 'bg-tertiary/15 text-tertiary',
    primary: 'bg-primary/15 text-primary',
    success: 'bg-success/15 text-success',
    warning: 'bg-warning/15 text-warning',
  }
  return (
    <div
      onClick={href ? () => navigate(href) : undefined}
      className={`glass-card rounded-xl p-5 ${href ? 'cursor-pointer hover:border-accent/20' : ''} transition-all`}
    >
      <div className="flex items-center gap-2.5 mb-3">
        <div className={`p-2 rounded-lg ${colorMap[color] ?? colorMap.accent}`}>
          <Icon size={16} />
        </div>
        <span className="text-[11px] uppercase tracking-widest text-on-surface-dim font-semibold">
          {label}
        </span>
      </div>
      <p className="text-2xl font-display font-bold tracking-tight text-on-surface">
        {typeof value === 'number' ? value.toLocaleString() : value}
      </p>
    </div>
  )
}

function timeAgo(dateStr: string) {
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'gerade eben'
  if (mins < 60) return `vor ${mins}m`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `vor ${hours}h`
  return `vor ${Math.floor(hours / 24)}d`
}

export function Dashboard() {
  const { data: otData } = useQuery(GET_ALL_OBJECT_TYPES, { pollInterval: 30000 })
  const { data: logData } = useQuery(GET_ACTION_LOG, { variables: { limit: 10 }, pollInterval: 15000 })
  const navigate = useNavigate()

  const objectTypes: ObjectTypeSchema[] = otData?.getAllObjectTypes ?? []
  const logs: ActionLogEntry[] = logData?.getActionLog ?? []
  const totalProps = objectTypes.reduce((sum, ot) => sum + ot.properties.length, 0)
  const totalLinks = objectTypes.reduce((sum, ot) => sum + ot.linkTypes.length, 0)

  // Empty state for new tenants
  if (objectTypes.length === 0 && logs.length === 0) {
    return (
      <div className="p-8 flex items-center justify-center min-h-[80vh]">
        <div className="glass-card rounded-2xl p-10 max-w-lg text-center">
          <Rocket size={48} className="text-accent mx-auto mb-6" />
          <h1 className="text-2xl font-display font-bold text-on-surface mb-2">
            Willkommen bei NEXO Fabric
          </h1>
          <p className="text-on-surface-dim mb-8">
            Starte in 3 Schritten:
          </p>
          <div className="space-y-3 text-left mb-8">
            {[
              { step: '1', label: 'Object Type definieren', href: '/ontology' },
              { step: '2', label: 'Connector verbinden', href: '/connectors' },
              { step: '3', label: 'AI Agent befragen', href: '/chat' },
            ].map(s => (
              <button
                key={s.step}
                onClick={() => navigate(s.href)}
                className="w-full flex items-center gap-4 p-4 rounded-xl bg-surface-high hover:bg-surface-bright transition-colors text-left"
              >
                <span className="w-8 h-8 rounded-full bg-accent/20 flex items-center justify-center text-sm font-bold text-accent">
                  {s.step}
                </span>
                <span className="flex-1 text-sm font-medium text-on-surface">{s.label}</span>
                <ArrowRight size={16} className="text-on-surface-dim" />
              </button>
            ))}
          </div>
          <button
            onClick={() => navigate('/ontology')}
            className="px-6 py-3 bg-accent text-white rounded-lg font-medium hover:bg-accent-dim transition-colors"
          >
            Ontology Builder öffnen
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="p-6 space-y-6 max-w-7xl">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-display font-bold text-on-surface">Dashboard</h1>
          <p className="text-sm text-on-surface-dim mt-0.5">Ontology Engine overview</p>
        </div>
        <button
          onClick={() => navigate('/ontology')}
          className="flex items-center gap-2 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-dim transition-colors"
        >
          <Plus size={14} />
          Neuer Object Type
        </button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-5 gap-3">
        <KpiCard icon={Boxes} label="Object Types" value={objectTypes.length} color="accent" href="/ontology" />
        <KpiCard icon={Database} label="Properties" value={totalProps} color="tertiary" />
        <KpiCard icon={GitFork} label="Link Types" value={totalLinks} color="primary" href="/graph" />
        <KpiCard icon={Activity} label="Objects" value="—" color="success" href="/objects" />
        <KpiCard icon={Activity} label="Actions" value={logs.length} color="warning" href="/actions" />
      </div>

      {/* Two-column */}
      <div className="grid grid-cols-2 gap-4">
        {/* Object Types */}
        <div className="glass-card rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-on-surface">Object Types</h2>
            <button onClick={() => navigate('/ontology')} className="text-xs text-accent hover:underline">
              Alle anzeigen
            </button>
          </div>
          <div className="space-y-1.5">
            {objectTypes.slice(0, 6).map((ot) => (
              <div
                key={ot.apiName}
                onClick={() => navigate(`/objects/${ot.apiName}`)}
                className="flex items-center justify-between p-3 rounded-lg bg-surface-low/50 hover:bg-surface-high cursor-pointer transition-colors"
              >
                <div className="flex items-center gap-3">
                  <div className="w-2 h-2 rounded-full bg-accent" />
                  <div>
                    <p className="text-sm font-medium text-on-surface">{ot.displayName}</p>
                    <p className="text-[11px] text-on-surface-dim font-mono">{ot.apiName}</p>
                  </div>
                </div>
                <span className="text-xs text-on-surface-dim bg-surface-highest px-2 py-0.5 rounded">
                  {ot.properties.length} props
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Recent Actions */}
        <div className="glass-card rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-on-surface">Recent Actions</h2>
            <button onClick={() => navigate('/actions')} className="text-xs text-accent hover:underline">
              Alle anzeigen
            </button>
          </div>
          <div className="space-y-1.5">
            {logs.slice(0, 6).map((log) => (
              <div
                key={log.id}
                className="flex items-center justify-between p-3 rounded-lg bg-surface-low/50 hover:bg-surface-high transition-colors"
              >
                <div>
                  <p className="text-sm font-medium text-on-surface">{log.actionType}</p>
                  <p className="text-[11px] text-on-surface-dim">
                    {log.performedBy} · {log.performedAt ? timeAgo(log.performedAt) : '—'}
                  </p>
                </div>
                <span
                  className={`text-[10px] uppercase tracking-wider font-bold px-2 py-0.5 rounded ${
                    log.status === 'SUCCESS'
                      ? 'bg-success/10 text-success'
                      : log.status === 'FAILED'
                        ? 'bg-error/10 text-error'
                        : 'bg-warning/10 text-warning'
                  }`}
                >
                  {log.status}
                </span>
              </div>
            ))}
            {logs.length === 0 && (
              <p className="text-sm text-on-surface-dim py-6 text-center">Noch keine Actions</p>
            )}
          </div>
        </div>
      </div>

      {/* Bottom row */}
      <div className="grid grid-cols-2 gap-4">
        {/* Semantic Search Health */}
        <div className="glass-card rounded-xl p-5">
          <div className="flex items-center gap-2 mb-3">
            <Cpu size={15} className="text-success" />
            <h2 className="text-sm font-semibold text-on-surface">Semantic Search</h2>
          </div>
          <div className="space-y-2">
            {[
              { label: 'Status', value: 'ONNX Active', color: 'text-success' },
              { label: 'Modell', value: 'all-MiniLM-L6-v2', color: 'text-on-surface' },
              { label: 'Dimensionen', value: '384', color: 'text-on-surface' },
            ].map(row => (
              <div key={row.label} className="flex justify-between text-sm">
                <span className="text-on-surface-dim">{row.label}</span>
                <span className={`font-mono text-xs ${row.color}`}>{row.value}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Connectors */}
        <div className="glass-card rounded-xl p-5">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <Plug size={15} className="text-accent" />
              <h2 className="text-sm font-semibold text-on-surface">Active Connectors</h2>
            </div>
            <button onClick={() => navigate('/connectors')} className="text-xs text-accent hover:underline">
              Verwalten
            </button>
          </div>
          <p className="text-sm text-on-surface-dim py-4 text-center">
            Noch keine Connectors konfiguriert
          </p>
        </div>
      </div>
    </div>
  )
}
