/** Wraps the words to highlight in the given class (bo-said for what was said, bo-better for the model). */
export function highlight(text: string, words: string[], className: string) {
  const wanted = new Set(words.map((w) => w.toLowerCase()));
  return text.split(/(\s+)/).map((part, i) => {
    const bare = part.replace(/[^A-Za-z0-9']/g, "").toLowerCase();
    return bare && wanted.has(bare) ? <span key={i} className={className}>{part}</span> : part;
  });
}
