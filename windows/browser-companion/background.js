const endpoint = 'http://127.0.0.1:59265';
let lastReport = '';
chrome.runtime.onMessage.addListener((message, sender, respond) => {
  if (message?.type !== 'luna-state' || !sender.tab || !sender.url) return;
  let origin;
  try { origin = new URL(sender.url).origin; } catch { return; }
  if (!['https://luna.amazon.co.uk', 'https://luna.amazon.com'].includes(origin)) return;
  const {phase, sequence} = message.state ?? {};
  if (!['waiting','buffering','playing','attention','ended'].includes(phase) || !Number.isSafeInteger(sequence) || sequence < 1) return;
  (async () => {
    const session = await fetch(endpoint + '/session', {method:'POST', cache:'no-store', mode:'cors', signal:AbortSignal.timeout(2000)}).then(r => r.ok ? r.json() : null);
    if (!session?.nonce) return;
    // Bind the requested game first, then continue reporting its tab when it
    // returns to Luna home. Never attach another already-open Luna game.
    let asin;
    try { asin = message.launchAsin || new URL(sender.tab.url || sender.url).searchParams.get('asin'); } catch { return; }
    if (session.tab != null ? sender.tab.id !== session.tab : asin !== session.asin) return;
    if (!sender.documentId) return;
    const result = await fetch(endpoint + '/state', {method:'POST', mode:'cors', headers:{'Content-Type':'application/json'}, body:JSON.stringify({nonce:session.nonce, tab:sender.tab.id, frame:sender.frameId, document:sender.documentId, phase, sequence}), signal:AbortSignal.timeout(2000)});
    const report = `${phase}:${result.status}`;
    if (report !== lastReport) { console.info('Luna companion state', report); lastReport = report; }
  })().then(() => respond({ok:true}), () => respond({ok:false}));
  return true;
});
