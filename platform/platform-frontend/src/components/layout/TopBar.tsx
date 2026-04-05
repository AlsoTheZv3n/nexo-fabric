import { Search, Bell, Command } from 'lucide-react'

export function TopBar() {
  const triggerCommandPalette = () => {
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', metaKey: true }))
  }

  return (
    <header className="fixed top-0 left-64 right-0 h-14 bg-surface-low/80 backdrop-blur-md flex items-center justify-between px-6 z-20 border-b border-outline-variant/10">
      {/* Left: Search trigger */}
      <button
        onClick={triggerCommandPalette}
        className="relative w-72 flex items-center gap-2 bg-surface-highest rounded-lg pl-3 pr-3 py-2 text-sm text-on-surface-dim hover:text-on-surface hover:bg-surface-bright transition-colors cursor-pointer"
      >
        <Search size={14} />
        <span className="flex-1 text-left text-[13px]">Search...</span>
        <kbd className="flex items-center gap-0.5 text-[10px] font-mono text-on-surface-dim/60 bg-surface-container px-1.5 py-0.5 rounded">
          <Command size={10} />K
        </kbd>
      </button>

      {/* Right: Notifications + Avatar */}
      <div className="flex items-center gap-1.5">
        <button className="p-2 rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-high transition-colors relative">
          <Bell size={17} />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-accent rounded-full" />
        </button>
        <div className="ml-2 flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface-high transition-colors cursor-pointer">
          <div className="w-7 h-7 rounded-full bg-accent/20 flex items-center justify-center text-[11px] font-semibold text-accent">
            N
          </div>
          <span className="text-[13px] font-medium text-on-surface hidden lg:block">NEXO</span>
        </div>
      </div>
    </header>
  )
}
