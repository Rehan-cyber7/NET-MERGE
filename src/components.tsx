import type { ReactNode } from 'react'
import { Cable, Signal, Wifi, X } from 'lucide-react'
import type { NetId } from './engine'
import type { Sample } from './store'

// PlusNet's own palette: Wi-Fi teal, the amber of its USB network for mobile data, steel for Ethernet.
const TONE: Record<NetId, string> = { wifi: 'wifi', cellular: 'usb', ethernet: 'ethernet' }
export const tone = (id: NetId): { solid: string; bg: string; text: string } => ({
  solid: `var(--color-${TONE[id]})`,
  bg: `var(--color-${TONE[id]}-bg)`,
  text: `var(--color-${TONE[id]}-text)`
})

const IDS: NetId[] = ['wifi', 'cellular', 'ethernet'] // what a digit in `cells` stands for

export function NetIcon({ id, size = 14 }: { id: NetId; size?: number }): React.JSX.Element {
  const Icon = id === 'wifi' ? Wifi : id === 'cellular' ? Signal : Cable
  return <Icon size={size} color={tone(id).solid} aria-hidden />
}

export function Sheet({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }): React.JSX.Element {
  return (
    <div className="fixed inset-0 z-50 flex items-end bg-black/50" onClick={onClose}>
      <div
        role="dialog"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
        style={{ animation: 'plusnet-sheet 0.2s ease-out' }}
        className="max-h-[90%] w-full overflow-y-auto rounded-t-2xl border-t border-border bg-background px-5 pt-3 pb-[calc(env(safe-area-inset-bottom)+20px)]"
      >
        <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-[var(--border-strong)]" />
        <div className="mb-4 flex items-center justify-between">
          <h2 className="min-w-0 truncate text-base font-bold">{title}</h2>
          <button aria-label="Close" onClick={onClose} className="p-1 text-muted-foreground">
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

export function Switch({ on, onChange, label, color, disabled }: { on: boolean; onChange: (v: boolean) => void; label: string; color: string; disabled?: boolean }): React.JSX.Element {
  return (
    <button
      role="switch"
      aria-checked={on}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!on)}
      style={{ background: on ? color : 'var(--track-bg)' }}
      className="relative h-7 w-12 shrink-0 rounded-full transition-colors disabled:opacity-40"
    >
      <span className={`absolute top-0.5 left-0.5 h-6 w-6 rounded-full bg-white shadow transition-transform ${on ? 'translate-x-5' : ''}`} />
    </button>
  )
}

export function ProgressBar({ pct, color = 'var(--color-accent)' }: { pct: number; color?: string }): React.JSX.Element {
  return (
    <div className="h-1.5 overflow-hidden rounded-full bg-[var(--track-bg)]">
      <div className="h-full rounded-full" style={{ width: `${Math.max(0, Math.min(100, pct))}%`, background: color }} />
    </div>
  )
}

/** One bar split by network: who is delivering how much of the speed (or, when done, of the file). */
export function ShareBar({ parts }: { parts: { id: NetId; value: number }[] }): React.JSX.Element {
  const sum = parts.reduce((a, p) => a + p.value, 0)
  return (
    <div className="flex h-1.5 gap-px overflow-hidden rounded-full bg-[var(--track-bg)]">
      {sum > 0 && parts.filter((p) => p.value > 0).map((p) => <div key={p.id} style={{ width: `${(p.value / sum) * 100}%`, background: tone(p.id).solid }} />)}
    </div>
  )
}

/** The file as small blocks, each colored by the network that delivered it (PlusNet's block grid). */
export function BlockGrid({ cells }: { cells: string }): React.JSX.Element | null {
  if (!cells) return null
  return (
    <div className="grid gap-[3px]" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(11px, 1fr))' }} aria-hidden>
      {[...cells].map((c, i) => {
        const net = c >= '0' && c <= '2' ? IDS[Number(c)] : undefined
        const style = net ? { background: tone(net).solid } : c === '*' ? { background: 'var(--color-accent)', animation: 'plusnet-glow 1.2s infinite' } : { background: 'var(--track-bg)' }
        return <div key={i} className="aspect-square rounded-[3px]" style={style} />
      })}
    </div>
  )
}

/** Speed over the last minute: one line per network, the total in the brand's node grey. */
export function Chart({ samples, ids }: { samples: Sample[]; ids: NetId[] }): React.JSX.Element | null {
  if (samples.length < 2) return null
  const W = 300
  const H = 70
  const max = Math.max(1, ...samples.map((s) => s.total))
  const line = (pick: (s: Sample) => number): string =>
    samples.map((s, i) => `${((i / (samples.length - 1)) * W).toFixed(1)},${(H - (pick(s) / max) * (H - 4) - 2).toFixed(1)}`).join(' ')
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="h-[70px] w-full" preserveAspectRatio="none" role="img" aria-label="Speed over the last minute">
      <polyline points={line((s) => s.total)} fill="none" stroke="var(--node-accent)" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
      {ids.map((id) => (
        <polyline key={id} points={line((s) => s.byNet[id] ?? 0)} fill="none" stroke={tone(id).solid} strokeWidth="2" vectorEffect="non-scaling-stroke" />
      ))}
    </svg>
  )
}
