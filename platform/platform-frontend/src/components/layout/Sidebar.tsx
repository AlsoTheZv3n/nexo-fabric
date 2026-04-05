import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  Bot,
  GitFork,
  Search,
  Network,
  Database,
  Zap,
  Scale,
  CreditCard,
  Settings,
  HelpCircle,
} from 'lucide-react'

const navGroups = [
  {
    label: 'WORKSPACE',
    items: [
      { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
      { to: '/chat', icon: Bot, label: 'AI Chat' },
    ],
  },
  {
    label: 'ONTOLOGY',
    items: [
      { to: '/ontology', icon: GitFork, label: 'Ontology Builder' },
      { to: '/objects', icon: Search, label: 'Object Explorer' },
      { to: '/graph', icon: Network, label: 'Graph View' },
    ],
  },
  {
    label: 'DATA',
    items: [
      { to: '/connectors', icon: Database, label: 'Connector Manager' },
      { to: '/actions', icon: Zap, label: 'Action Center' },
      { to: '/resolution', icon: Scale, label: 'Resolution Center' },
    ],
  },
  {
    label: 'SYSTEM',
    items: [
      { to: '/billing', icon: CreditCard, label: 'Billing & Usage' },
    ],
  },
]

export function Sidebar() {
  return (
    <aside className="fixed left-0 top-0 w-64 h-screen bg-surface-low flex flex-col z-30 border-r border-outline-variant/10">
      {/* Logo */}
      <div className="px-5 pt-6 pb-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-primary-container">
            <GitFork size={18} className="text-accent" />
          </div>
          <div>
            <h1 className="font-display text-base font-bold text-on-surface leading-tight">
              NEXO Fabric
            </h1>
            <p className="text-[10px] uppercase tracking-widest text-on-surface-dim leading-tight">
              Ontology Platform
            </p>
          </div>
        </div>
      </div>

      {/* Navigation Groups */}
      <nav className="flex-1 px-3 overflow-y-auto space-y-5 pb-4">
        {navGroups.map((group) => (
          <div key={group.label}>
            <p className="px-3 mb-1.5 text-[10px] font-semibold uppercase tracking-widest text-on-surface-dim/60">
              {group.label}
            </p>
            <div className="space-y-0.5">
              {group.items.map(({ to, icon: Icon, label }) => (
                <NavLink
                  key={to}
                  to={to}
                  end={to === '/'}
                  className={({ isActive }) =>
                    `flex items-center gap-3 px-3 py-2 rounded-lg text-[13px] font-medium transition-colors ${
                      isActive
                        ? 'bg-accent/10 text-accent border-l-2 border-accent -ml-0.5 pl-3.5'
                        : 'text-on-surface-dim hover:bg-surface-high hover:text-on-surface'
                    }`
                  }
                >
                  <Icon size={16} strokeWidth={1.8} />
                  {label}
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>

      {/* Bottom Links */}
      <div className="px-3 py-2 space-y-0.5 border-t border-outline-variant/10">
        <NavLink
          to="/settings"
          className={({ isActive }) =>
            `flex items-center gap-3 px-3 py-2 rounded-lg text-[13px] font-medium transition-colors ${
              isActive ? 'bg-accent/10 text-accent' : 'text-on-surface-dim hover:bg-surface-high hover:text-on-surface'
            }`
          }
        >
          <Settings size={16} strokeWidth={1.8} />
          Settings
        </NavLink>
        <NavLink
          to="/support"
          className={({ isActive }) =>
            `flex items-center gap-3 px-3 py-2 rounded-lg text-[13px] font-medium transition-colors ${
              isActive ? 'bg-accent/10 text-accent' : 'text-on-surface-dim hover:bg-surface-high hover:text-on-surface'
            }`
          }
        >
          <HelpCircle size={16} strokeWidth={1.8} />
          Support
        </NavLink>
      </div>

      {/* User Card */}
      <div className="px-4 pb-5 pt-2">
        <div className="flex items-center gap-3 px-3 py-3 rounded-lg bg-surface-container">
          <div className="w-8 h-8 rounded-full bg-accent/20 flex items-center justify-center text-xs font-semibold text-accent">
            N
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-on-surface truncate">NEXO Admin</p>
            <p className="text-[11px] text-on-surface-dim truncate">Owner</p>
          </div>
        </div>
      </div>
    </aside>
  )
}
