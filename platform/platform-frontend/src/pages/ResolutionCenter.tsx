import { Scale, RefreshCw } from 'lucide-react'

export function ResolutionCenter() {
  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-display font-bold text-on-surface">Resolution Center</h1>
          <p className="text-sm text-on-surface-dim mt-1">Duplikaterkennung & Entity Resolution</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-accent/10 text-accent rounded-lg text-sm font-medium hover:bg-accent/20 transition-colors">
          <RefreshCw size={14} />
          Scan starten
        </button>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="glass-card rounded-xl p-4 text-center">
          <p className="text-2xl font-bold text-warning">0</p>
          <p className="text-xs text-on-surface-dim mt-1">Offen</p>
        </div>
        <div className="glass-card rounded-xl p-4 text-center">
          <p className="text-2xl font-bold text-success">0</p>
          <p className="text-xs text-on-surface-dim mt-1">Gemerged</p>
        </div>
        <div className="glass-card rounded-xl p-4 text-center">
          <p className="text-2xl font-bold text-on-surface-dim">0</p>
          <p className="text-xs text-on-surface-dim mt-1">Abgelehnt</p>
        </div>
      </div>

      <div className="glass-card rounded-xl p-8 flex flex-col items-center justify-center text-center">
        <Scale size={40} className="text-on-surface-dim/30 mb-4" />
        <p className="text-on-surface-dim">Keine Duplikat-Kandidaten gefunden</p>
        <p className="text-sm text-on-surface-dim/60 mt-1">Starte einen Scan um potenzielle Duplikate zu erkennen</p>
      </div>
    </div>
  )
}
