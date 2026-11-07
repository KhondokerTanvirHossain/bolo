/** Clips kept on this device only (ONB-4: a guest's Day 1 clip never leaves the device before sign-in).
 *  IndexedDB where it works; an in-memory copy otherwise (private windows), so playback still works this visit. */
const DB = "bolo";
const STORE = "clips";
const memory = new Map<string, Blob>();

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function tx<T>(mode: IDBTransactionMode, run: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return open().then((db) => new Promise<T>((resolve, reject) => {
    const req = run(db.transaction(STORE, mode).objectStore(STORE));
    req.onsuccess = () => { resolve(req.result); db.close(); };
    req.onerror = () => { reject(req.error); db.close(); };
  }));
}

export async function saveClip(key: string, blob: Blob): Promise<void> {
  memory.set(key, blob);
  try { await tx("readwrite", (s) => s.put(blob, key)); } catch { /* memory copy only */ }
}

export async function loadClip(key: string): Promise<Blob | null> {
  try {
    const stored = await tx<Blob | undefined>("readonly", (s) => s.get(key));
    if (stored) return stored;
  } catch { /* fall through to memory */ }
  return memory.get(key) ?? null;
}
