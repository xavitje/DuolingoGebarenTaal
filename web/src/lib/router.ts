import { Store } from './store';

export const route = new Store<string>(location.pathname);

export function navigate(path: string, replace = false) {
  if (path === location.pathname && !replace) return;
  history[replace ? 'replaceState' : 'pushState'](null, '', path);
  route.set(location.pathname);
  window.scrollTo(0, 0);
}

window.addEventListener('popstate', () => route.set(location.pathname));

/** '/klas/:id' tegen '/klas/abc' -> { id: 'abc' }; geen match -> null. */
export function match(pattern: string, path: string): Record<string, string> | null {
  const p = pattern.split('/').filter(Boolean);
  const a = path.split('/').filter(Boolean);
  if (p.length !== a.length) return null;
  const params: Record<string, string> = {};
  for (let i = 0; i < p.length; i++) {
    if (p[i].startsWith(':')) params[p[i].slice(1)] = decodeURIComponent(a[i]);
    else if (p[i] !== a[i]) return null;
  }
  return params;
}

/** Interne link zonder herladen; met Ctrl/Cmd-klik gewoon een nieuw tabblad. */
export function onLinkClick(e: MouseEvent) {
  const anchor = (e.currentTarget as HTMLAnchorElement) ?? null;
  if (!anchor || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
  const href = anchor.getAttribute('href');
  if (!href || !href.startsWith('/') || anchor.target) return;
  e.preventDefault();
  navigate(href);
}
