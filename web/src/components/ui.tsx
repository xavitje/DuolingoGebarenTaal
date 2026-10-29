import type { ComponentChildren, JSX } from 'preact';
import { useEffect, useRef } from 'preact/hooks';
import { onLinkClick } from '../lib/router';

type ButtonProps = JSX.HTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'on-night';
  size?: 'small' | 'large';
  block?: boolean;
  busy?: boolean;
  type?: 'button' | 'submit';
  disabled?: boolean;
};

export function Button({ variant = 'primary', size, block, busy, class: cls, children, disabled, type = 'button', ...rest }: ButtonProps) {
  const classes = ['btn', `btn-${variant}`, size ? `btn-${size}` : '', block ? 'btn-block' : '', cls ?? ''].filter(Boolean).join(' ');
  return (
    <button {...rest} type={type} class={classes} disabled={disabled || busy} aria-busy={busy ? 'true' : undefined}>
      {busy ? <span class="spinner spinner-inline" aria-hidden="true" /> : null}
      {children}
    </button>
  );
}

export function LinkButton({ href, variant = 'primary', size, block, children, class: cls }: {
  href: string; variant?: 'primary' | 'secondary' | 'ghost' | 'on-night'; size?: 'small' | 'large'; block?: boolean; children: ComponentChildren; class?: string;
}) {
  const classes = ['btn', `btn-${variant}`, size ? `btn-${size}` : '', block ? 'btn-block' : '', cls ?? ''].filter(Boolean).join(' ');
  return (
    <a href={href} class={classes} onClick={onLinkClick}>
      {children}
    </a>
  );
}

export function Link({ href, children, class: cls }: { href: string; children: ComponentChildren; class?: string }) {
  return (
    <a href={href} class={cls} onClick={onLinkClick}>
      {children}
    </a>
  );
}

export function Spinner({ label = 'Laden…' }: { label?: string }) {
  return (
    <div class="loading" role="status">
      <span class="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function Notice({ kind = 'error', children }: { kind?: 'error' | 'info' | 'success' | 'warning'; children: ComponentChildren }) {
  return (
    <div class={`notice notice-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

/** Modaal venster met de native <dialog>: focus en Escape regelt de browser. */
export function Dialog({ open, title, onClose, children, wide }: {
  open: boolean; title: string; onClose: () => void; children: ComponentChildren; wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog ref={ref} class={`dialog${wide ? ' dialog-wide' : ''}`} onClose={onClose} onCancel={(e) => { e.preventDefault(); onClose(); }} aria-labelledby="dialog-title">
      {open ? (
        <div class="dialog-body">
          <div class="dialog-head">
            <h2 id="dialog-title" class="heading">{title}</h2>
            <button type="button" class="icon-btn" onClick={onClose} aria-label="Sluiten">
              <Icon name="close" />
            </button>
          </div>
          {children}
        </div>
      ) : null}
    </dialog>
  );
}

export function Field({ label, hint, error, children, id }: { label: string; hint?: string; error?: string | null; children: ComponentChildren; id: string }) {
  return (
    <div class="field">
      <label for={id}>{label}</label>
      {children}
      {hint ? <span class="caption" id={`${id}-hint`}>{hint}</span> : null}
      {error ? <span class="field-error" id={`${id}-error`}>{error}</span> : null}
    </div>
  );
}

const paths: Record<string, string> = {
  close: 'M6 6l12 12M18 6L6 18',
  back: 'M15 5l-7 7 7 7',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  lock: 'M7 11V8a5 5 0 0110 0v3M6 11h12v9H6z',
  home: 'M4 11l8-7 8 7v9h-5v-6H9v6H4z',
  star: 'M12 4l2.4 5 5.6.7-4.1 3.8 1 5.5L12 16.4 7.1 19l1-5.5L4 9.7 9.6 9z',
  users: 'M9 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7zM2.5 20c.7-3.5 3.3-5.5 6.5-5.5s5.8 2 6.5 5.5M16 4.6a3.5 3.5 0 010 6.8M18 14.8c1.9.7 3.2 2.5 3.6 5.2',
  user: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4.5 20.5c.8-4 3.8-6 7.5-6s6.7 2 7.5 6',
  video: 'M3 7a2 2 0 012-2h9a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2zM16 10.5l5-3v9l-5-3',
  play: 'M8 5.5v13l11-6.5z',
  camera: 'M4 8a2 2 0 012-2h2l1.5-2h5L16 6h2a2 2 0 012 2v9a2 2 0 01-2 2H6a2 2 0 01-2-2zM12 16.5a3.5 3.5 0 100-7 3.5 3.5 0 000 7z',
  print: 'M7 9V4h10v5M7 17H5a2 2 0 01-2-2v-4a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2h-2M7 14h10v6H7z',
  refresh: 'M20 11a8 8 0 10-2.3 5.7M20 5v6h-6',
  plus: 'M12 5v14M5 12h14',
  flame: 'M12 3c1 3.5 5 5.5 5 10a5 5 0 01-10 0c0-2.4 1.2-4 2.5-5.2.3 1.6 1 2.6 2 3.2-.3-3 .2-5.6.5-8z',
  gem: 'M6 4h12l3 5-9 11L3 9zM3 9h18M9.5 4L8 9l4 11 4-11-1.5-5',
  hand: 'M8 13V5.5a1.5 1.5 0 013 0V11m0-6.5a1.5 1.5 0 013 0V11m0-5a1.5 1.5 0 013 0v7.5c0 4-2.5 7-6.5 7-3 0-4.5-1.6-6-4.5L4.2 12.7a1.5 1.5 0 012.6-1.5L8 13',
  logout: 'M15 4h3a2 2 0 012 2v12a2 2 0 01-2 2h-3M10 16l-4-4 4-4M6 12h10',
  shield: 'M12 3l7 3v5c0 4.5-3 8.3-7 10-4-1.7-7-5.5-7-10V6z',
};

export function Icon({ name, size = 24, label }: { name: keyof typeof paths | string; size?: number; label?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"
      role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : 'true'}>
      <path d={paths[name] ?? ''} />
    </svg>
  );
}

/** Ronde score-indicator, zoals ScoreRing in de app. */
export function ScoreRing({ score }: { score: number }) {
  const r = 26;
  const c = 2 * Math.PI * r;
  const pct = Math.round(Math.max(0, Math.min(1, score)) * 100);
  const color = score >= 0.9 ? 'var(--clover)' : score >= 0.6 ? 'var(--gold)' : 'var(--coral)';
  return (
    <div class="score-ring" aria-hidden="true">
      <svg width="64" height="64" viewBox="0 0 64 64">
        <circle cx="32" cy="32" r={r} stroke="rgba(255,248,240,0.15)" stroke-width="7" fill="none" />
        <circle cx="32" cy="32" r={r} stroke={color} stroke-width="7" fill="none" stroke-linecap="round"
          stroke-dasharray={c} stroke-dashoffset={c * (1 - pct / 100)} transform="rotate(-90 32 32)" />
      </svg>
      <span>{pct}%</span>
    </div>
  );
}

export function RepDots({ done, required }: { done: number; required: number }) {
  return (
    <div class="rep-dots">
      <span class="dots" aria-hidden="true">
        {Array.from({ length: required }, (_, i) => <span class={`dot${i < done ? ' dot-done' : ''}`} />)}
      </span>
      <span>{done} van {required} keer goed</span>
    </div>
  );
}

export function SuccessMark() {
  return (
    <div class="success-mark" aria-hidden="true">
      <Icon name="check" size={56} />
    </div>
  );
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso.length === 10 ? `${iso}T12:00:00` : iso);
  return d.toLocaleDateString('nl-NL', { day: 'numeric', month: 'long', year: 'numeric' });
}
