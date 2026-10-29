import type { ComponentChildren } from 'preact';
import { auth, canContribute, isStaff } from '../lib/auth';
import { onLinkClick, route } from '../lib/router';
import { useStore } from '../lib/store';
import { Icon } from './ui';

export function Shell({ children }: { children: ComponentChildren }) {
  const path = useStore(route);
  const a = useStore(auth);
  const tabs = [
    { href: '/leren', label: 'Leren', icon: 'home' },
    { href: '/voortgang', label: 'Voortgang', icon: 'star' },
    ...(isStaff(a) ? [{ href: '/klas', label: 'Klas', icon: 'users' }] : []),
    ...(canContribute(a) ? [{ href: '/bijdragen', label: 'Bijdragen', icon: 'video' }] : []),
    { href: '/profiel', label: 'Profiel', icon: 'user' },
  ];
  const current = (href: string) => path === href || path.startsWith(`${href}/`);

  return (
    <>
      <a class="skip-link" href="#main">Naar de inhoud</a>
      <header class="topbar">
        <div class="topbar-inner">
          <a href="/leren" class="wordmark" onClick={onLinkClick}>SignCoach</a>
          <nav class="tabs" aria-label="Hoofdmenu">
            {tabs.map((t) => (
              <a key={t.href} href={t.href} class="tab" aria-current={current(t.href) ? 'page' : undefined} onClick={onLinkClick}>
                <Icon name={t.icon} size={22} />
                <span>{t.label}</span>
              </a>
            ))}
          </nav>
        </div>
      </header>
      <main id="main" tabIndex={-1}>{children}</main>
    </>
  );
}
