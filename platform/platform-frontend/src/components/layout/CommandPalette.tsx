import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Search,
  LayoutDashboard,
  GitFork,
  Bot,
  Database,
  Zap,
  Network,
  Settings,
  Plus,
} from 'lucide-react'

interface CommandItem {
  id: string
  label: string
  description?: string
  icon: React.ReactNode
  action: () => void
  group: string
}

export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [query, setQuery] = useState('')
  const [selectedIndex, setSelectedIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()

  const commands: CommandItem[] = [
    { id: 'dash', label: 'Dashboard', icon: <LayoutDashboard size={16} />, action: () => navigate('/'), group: 'Navigation' },
    { id: 'chat', label: 'AI Chat', description: 'Frag den Ontology Agent', icon: <Bot size={16} />, action: () => navigate('/chat'), group: 'Navigation' },
    { id: 'onto', label: 'Ontology Builder', icon: <GitFork size={16} />, action: () => navigate('/ontology'), group: 'Navigation' },
    { id: 'expl', label: 'Object Explorer', icon: <Search size={16} />, action: () => navigate('/objects'), group: 'Navigation' },
    { id: 'graph', label: 'Graph View', icon: <Network size={16} />, action: () => navigate('/graph'), group: 'Navigation' },
    { id: 'conn', label: 'Connector Manager', icon: <Database size={16} />, action: () => navigate('/connectors'), group: 'Navigation' },
    { id: 'act', label: 'Action Center', icon: <Zap size={16} />, action: () => navigate('/actions'), group: 'Navigation' },
    { id: 'set', label: 'Settings', icon: <Settings size={16} />, action: () => navigate('/settings'), group: 'Navigation' },
    { id: 'new-ot', label: 'Neuer Object Type', description: 'Object Type erstellen', icon: <Plus size={16} />, action: () => navigate('/ontology'), group: 'Quick Actions' },
    { id: 'new-conn', label: 'Neuer Connector', description: 'Datenquelle verbinden', icon: <Plus size={16} />, action: () => navigate('/connectors'), group: 'Quick Actions' },
  ]

  const filtered = query
    ? commands.filter(c =>
        c.label.toLowerCase().includes(query.toLowerCase()) ||
        c.description?.toLowerCase().includes(query.toLowerCase())
      )
    : commands

  const groups = [...new Set(filtered.map(c => c.group))]

  useEffect(() => {
    setSelectedIndex(0)
    setQuery('')
    if (open) inputRef.current?.focus()
  }, [open])

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        open ? onClose() : document.dispatchEvent(new CustomEvent('open-command-palette'))
      }
      if (e.key === 'Escape' && open) onClose()
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [open, onClose])

  useEffect(() => {
    const handler = () => {
      if (!open) {
        // Parent TopBar will handle opening
      }
    }
    document.addEventListener('open-command-palette', handler)
    return () => document.removeEventListener('open-command-palette', handler)
  }, [open])

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIndex(i => Math.min(i + 1, filtered.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIndex(i => Math.max(i - 1, 0))
    } else if (e.key === 'Enter' && filtered[selectedIndex]) {
      filtered[selectedIndex].action()
      onClose()
    }
  }

  if (!open) return null

  let flatIndex = -1

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[15vh]" onClick={onClose}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
      <div
        className="relative w-full max-w-lg bg-surface-container border border-outline-variant/20 rounded-xl shadow-2xl overflow-hidden"
        onClick={e => e.stopPropagation()}
      >
        {/* Search Input */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-outline-variant/10">
          <Search size={16} className="text-on-surface-dim" />
          <input
            ref={inputRef}
            value={query}
            onChange={e => { setQuery(e.target.value); setSelectedIndex(0) }}
            onKeyDown={handleKeyDown}
            placeholder="Suche oder Aktion..."
            className="flex-1 bg-transparent text-sm text-on-surface placeholder:text-on-surface-dim outline-none"
          />
          <kbd className="text-[10px] font-mono text-on-surface-dim/50 bg-surface-high px-1.5 py-0.5 rounded">
            ESC
          </kbd>
        </div>

        {/* Results */}
        <div className="max-h-80 overflow-y-auto py-2">
          {groups.map(group => (
            <div key={group}>
              <p className="px-4 pt-2 pb-1 text-[10px] font-semibold uppercase tracking-widest text-on-surface-dim/50">
                {group}
              </p>
              {filtered
                .filter(c => c.group === group)
                .map(cmd => {
                  flatIndex++
                  const idx = flatIndex
                  return (
                    <button
                      key={cmd.id}
                      onClick={() => { cmd.action(); onClose() }}
                      className={`w-full flex items-center gap-3 px-4 py-2.5 text-left transition-colors ${
                        idx === selectedIndex
                          ? 'bg-accent/10 text-accent'
                          : 'text-on-surface hover:bg-surface-high'
                      }`}
                    >
                      <span className="text-on-surface-dim">{cmd.icon}</span>
                      <span className="text-[13px] font-medium">{cmd.label}</span>
                      {cmd.description && (
                        <span className="text-[11px] text-on-surface-dim ml-auto">{cmd.description}</span>
                      )}
                    </button>
                  )
                })}
            </div>
          ))}
          {filtered.length === 0 && (
            <p className="px-4 py-6 text-center text-sm text-on-surface-dim">Keine Ergebnisse</p>
          )}
        </div>
      </div>
    </div>
  )
}
