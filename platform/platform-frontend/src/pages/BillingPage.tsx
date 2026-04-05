import { CreditCard, Key, BarChart3 } from 'lucide-react'
import { useState } from 'react'

const tabs = ['Übersicht', 'API Keys', 'Nutzungshistorie'] as const

export function BillingPage() {
  const [activeTab, setActiveTab] = useState<typeof tabs[number]>('Übersicht')

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-display font-bold text-on-surface">Billing & Usage</h1>
        <p className="text-sm text-on-surface-dim mt-1">Plan, API Keys und Nutzungsmetriken</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-surface-container rounded-lg p-1">
        {tabs.map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`flex-1 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              activeTab === tab
                ? 'bg-surface-high text-on-surface'
                : 'text-on-surface-dim hover:text-on-surface'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {activeTab === 'Übersicht' && (
        <div className="space-y-4">
          <div className="glass-card rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <div>
                <p className="text-lg font-bold text-on-surface">FREE Plan</p>
                <p className="text-sm text-on-surface-dim">Aktuelle Nutzung</p>
              </div>
              <button className="px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-dim transition-colors">
                Plan upgraden
              </button>
            </div>
            <div className="space-y-3">
              {[
                { label: 'Object Types', used: 0, max: 10 },
                { label: 'Objects', used: 0, max: 10000 },
                { label: 'Connectors', used: 0, max: 3 },
              ].map(m => (
                <div key={m.label}>
                  <div className="flex justify-between text-sm mb-1">
                    <span className="text-on-surface-dim">{m.label}</span>
                    <span className="text-on-surface font-mono">{m.used.toLocaleString()} / {m.max.toLocaleString()}</span>
                  </div>
                  <div className="h-2 bg-surface-highest rounded-full overflow-hidden">
                    <div
                      className="h-full bg-accent rounded-full transition-all"
                      style={{ width: `${Math.min((m.used / m.max) * 100, 100)}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {activeTab === 'API Keys' && (
        <div className="glass-card rounded-xl p-6">
          <div className="flex items-center justify-between mb-4">
            <p className="text-sm font-medium text-on-surface">API Keys</p>
            <button className="flex items-center gap-2 px-3 py-1.5 bg-accent/10 text-accent rounded-lg text-sm hover:bg-accent/20 transition-colors">
              <Key size={14} />
              Neuen Key erstellen
            </button>
          </div>
          <div className="text-center py-8">
            <Key size={32} className="text-on-surface-dim/30 mx-auto mb-3" />
            <p className="text-sm text-on-surface-dim">Noch keine API Keys erstellt</p>
          </div>
        </div>
      )}

      {activeTab === 'Nutzungshistorie' && (
        <div className="glass-card rounded-xl p-6">
          <div className="text-center py-8">
            <BarChart3 size={32} className="text-on-surface-dim/30 mx-auto mb-3" />
            <p className="text-sm text-on-surface-dim">Nutzungshistorie wird geladen...</p>
          </div>
        </div>
      )}
    </div>
  )
}
