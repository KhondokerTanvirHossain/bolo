/** Clips kept on this device only (ONB-4: a guest's Day 1 clip never leaves the device before sign-in).
 *  The in-memory copy is the source for this visit; IndexedDB keeps it across visits when it works. Nothing waits on
 *  IndexedDB for long: it can hang (a known Safari bug) or be missing (private windows). */
const DB = "bolo";
const STORE = "clips";
const READ_DEADLINE_MS = 1500;
const memory = new Map<string, Blob>();

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function withStore<T>(mode: IDBTransactionMode, run: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await open();
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction(STORE, mode);
      const req = run(tx.objectStore(STORE));
      tx.oncomplete = () => resolve(req.result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

export async function saveClip(key: string, blob: Blob): Promise<void> {
  memory.set(key, blob);
  // Persist in the background; the learner never waits for it.
  void withStore("readwrite", (s) => s.put(blob, key)).catch(() => {});
}

/** Removes every clip whose key starts with {@code prefix} (intro re-records on sign-out, delete everything). */
export async function deleteClips(prefix: string): Promise<void> {
  for (const k of [...memory.keys()]) if (k.startsWith(prefix)) memory.delete(k);
  try {
    const db = await open();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      const store = tx.objectStore(STORE);
      const req = store.getAllKeys();
      req.onsuccess = () => { for (const k of req.result) if (String(k).startsWith(prefix)) store.delete(k); };
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    db.close();
  } catch {
    // nothing stored, or storage unavailable
  }
}

/** Removes a clip from this device (sign-out). */
export async function deleteClip(key: string): Promise<void> {
  memory.delete(key);
  try {
    const db = await open();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).delete(key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // nothing stored, or storage unavailable
  }
}

export async function loadClip(key: string): Promise<Blob | null> {
  const inMemory = memory.get(key);
  if (inMemory) return inMemory;
  const stored = await Promise.race([
    withStore<Blob | undefined>("readonly", (s) => s.get(key)).catch(() => undefined),
    new Promise<undefined>((r) => setTimeout(() => r(undefined), READ_DEADLINE_MS)),
  ]);
  return stored ?? null;
}
