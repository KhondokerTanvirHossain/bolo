import type { Metadata } from "next";

// Discretion: the tab title is always just "Bolo" (design system), here too.
export const metadata: Metadata = { title: "Bolo" };

const section = { margin: "28px 0 0" } as const;

/** Plain words, from what Bolo actually stores (ACC-3, ACC-4, NFR-16). Linked from Google's sign-in consent screen. */
export default function PrivacyPage() {
  return (
    // The app root fills the screen and doesn't scroll (the talk scenes need that), so this page scrolls itself.
    <div style={{ height: "100%", overflowY: "auto" }}>
    <main style={{ maxWidth: 680, margin: "0 auto", padding: "40px 16px 64px", color: "var(--ink)", font: "500 17px/27px var(--font-sans)" }}>
      <h1 className="bo-flow__title">Privacy at Bolo</h1>
      <p>Bolo helps you practise speaking English. Here is what it keeps about you, and how to remove it. Updated 9 November 2026.</p>

      <h2 className="title-2" style={section}>What Bolo keeps</h2>
      <ul>
        <li>Your answers to the setup questions (what you need English for, the hardest moment, how long you want to talk).</li>
        <li>What you said in each talk, as text, and the scores the feedback is based on.</li>
        <li>Your voice recordings, only if you chose &quot;keep them&quot; at the start. You choose this before Bolo ever listens.</li>
        <li>Your Day 1 clip. It stays in your browser; it goes with your account only if you chose to keep recordings.</li>
        <li>When you sign in with Google: your email address and name.</li>
        <li>Simple app events, such as &quot;finished the setup&quot; or how long the first word took. They never contain your words or your voice.</li>
      </ul>

      <h2 className="title-2" style={section}>Why</h2>
      <p>To give you feedback after each talk, to show how you are improving, and to make Bolo work better. Bolo doesn&apos;t sell your data and doesn&apos;t show ads.</p>

      <h2 className="title-2" style={section}>For how long</h2>
      <ul>
        <li>Recordings you chose to keep: 90 days. Your Day 1 clip stays until you delete everything, for the Day 7, 14 and 21 comparison.</li>
        <li>If you chose not to keep recordings: your audio is deleted within 24 hours. The text and scores stay, so your progress still works.</li>
        <li>The full audio of a talk is deleted once your feedback is ready; only the short clips on your feedback cards are kept, by the rules above.</li>
        <li>Everything else stays until you delete it.</li>
      </ul>

      <h2 className="title-2" style={section}>Who processes it</h2>
      <ul>
        <li>Microsoft Azure Speech: turns your speech into text, scores pronunciation, and gives Bolo and your coach their voices.</li>
        <li>Anthropic: writes Bolo&apos;s replies and your coach&apos;s feedback from the text of your talk.</li>
        <li>Google Firebase: signs you in with your Google account.</li>
      </ul>
      <p>They process your data only to provide these parts of Bolo. Bolo&apos;s own server stores everything else, and audio is encrypted there.</p>

      <h2 className="title-2" style={section}>Deleting your data</h2>
      <p>In Me → Privacy you can stop keeping recordings, delete a recording, or delete everything. Deleting everything removes your account and all your data within 7 days.</p>
      <p>One thing stays, so the free plan can&apos;t be reset by deleting and signing in again: a one-way code made from your Google account and the dates of your free talks, kept for 12 months. It can&apos;t be turned back into your email, and it holds none of your words or your voice.</p>
      <h2 className="title-2" style={section}>Questions</h2>
      <p>Write to <a href="mailto:k.tanvir.hossain@gmail.com">k.tanvir.hossain@gmail.com</a> about your data, and we will answer or delete it within 7 days.</p>
      <p>See also the <a href="/terms">Terms of Use</a> and the <a href="/refund">Refund policy</a>.</p>
    </main>
    </div>
  );
}
