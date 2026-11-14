/* Bolo's service worker: shows the daily reminder (PRG-5) and opens Bolo when it's tapped. Nothing is cached. */
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = {};
  }
  const title = data.title || "Bolo";
  event.waitUntil(self.registration.showNotification(title, {
    body: data.body || "Time for today's talk.",
    icon: "/icons/icon-192.png",
    badge: "/icons/badge-96.png",
    tag: data.tag || "bolo-daily",
    data: { url: data.url || "/" },
  }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = new URL((event.notification.data && event.notification.data.url) || "/", self.location.origin).href;
  event.waitUntil((async () => {
    const open = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    for (const client of open) {
      if (new URL(client.url).origin === self.location.origin && "focus" in client) {
        await client.focus();
        if ("navigate" in client) await client.navigate(url);
        return;
      }
    }
    await self.clients.openWindow(url);
  })());
});

// The browser replaced its subscription: subscribe again with the same key and tell Bolo (review, reminders I6).
self.addEventListener("pushsubscriptionchange", (event) => {
  event.waitUntil((async () => {
    const key = event.oldSubscription && event.oldSubscription.options && event.oldSubscription.options.applicationServerKey;
    const sub = event.newSubscription || (key ? await self.registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key }) : null);
    if (!sub) return;
    const json = sub.toJSON();
    await fetch("/api/reminder/subscriptions", {
      method: "POST", credentials: "same-origin",
      headers: { "Content-Type": "application/json", "X-Bolo": "1" },
      body: JSON.stringify({ endpoint: json.endpoint, keys: json.keys }),
    });
  })());
});
