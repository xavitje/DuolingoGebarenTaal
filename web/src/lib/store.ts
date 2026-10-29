import { useEffect, useState } from 'preact/hooks';

/** Kleine gedeelde staat met abonnees; genoeg voor deze app zonder extra bibliotheek. */
export class Store<T> {
  private listeners = new Set<(value: T) => void>();
  constructor(private value: T) {}

  get(): T {
    return this.value;
  }

  set(next: T | ((prev: T) => T)): void {
    this.value = typeof next === 'function' ? (next as (prev: T) => T)(this.value) : next;
    for (const l of this.listeners) l(this.value);
  }

  subscribe(listener: (value: T) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}

export function useStore<T>(store: Store<T>): T {
  const [value, setValue] = useState(store.get());
  useEffect(() => {
    setValue(store.get());
    return store.subscribe(setValue);
  }, [store]);
  return value;
}

/** localStorage kan ontbreken of weigeren (privévenster, geblokkeerde opslag). */
export const local = {
  get<T>(key: string, fallback: T): T {
    try {
      const raw = localStorage.getItem(key);
      return raw == null ? fallback : (JSON.parse(raw) as T);
    } catch {
      return fallback;
    }
  },
  set(key: string, value: unknown): void {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {
      /* opslag niet beschikbaar: alleen deze sessie */
    }
  },
  remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      /* negeren */
    }
  },
};
