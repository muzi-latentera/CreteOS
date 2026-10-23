// Observe live media activity and the requested game ASIN only. Never transmit
// page text, cookies, credentials, account IDs, URLs or video/audio content.
(() => {
  const launchUrl = new URL(location.href);
  const launchAsin = launchUrl.searchParams.get('asin') || launchUrl.pathname.match(/^\/game\/[^/]+\/(B[A-Z0-9]{9})(?:\/|$)/i)?.[1] || null;
  let previous = new WeakMap(), seen = false, played = false, absentAt = 0, sequence = 0;
  let inflight = false;
  const snapshot = () => {
    let live = false, advancing = false;
    for (const video of document.querySelectorAll('video')) {
      const stream = video.srcObject;
      if (!stream || typeof stream.getVideoTracks !== 'function' || !stream.getVideoTracks().some(t => t.readyState === 'live')) continue;
      live = true;
      const frames = video.getVideoPlaybackQuality?.().totalVideoFrames ?? 0;
      const time = Number(video.currentTime) || 0;
      const old = previous.get(video);
      advancing ||= old !== undefined && (frames > old.frames || time > old.time) && !video.paused && video.readyState >= 2;
      previous.set(video, {frames,time});
    }
    if (live) { seen = true; absentAt = 0; }
    else if (seen && !absentAt) absentAt = performance.now();
    if (advancing) played = true;
    const ended = played && !live && performance.now() - absentAt >= 1000;
    const prompt = document.querySelector('input[type="password"], [role="alertdialog"], #full_screen_dialog_overlay');
    const attention = !!prompt && (prompt.checkVisibility ? prompt.checkVisibility({checkOpacity:true,checkVisibilityCSS:true}) : prompt.getClientRects ? prompt.getClientRects().length > 0 : true);
    return { sequence: ++sequence, phase: ended ? 'ended' : attention ? 'attention' : advancing ? 'playing' : live ? 'buffering' : 'waiting' };
  };
  async function tick() {
    if (inflight) return;
    inflight = true;
    try { await chrome.runtime.sendMessage({ type: 'luna-state', launchAsin, state: snapshot() }); }
    catch { /* CreteOS/extension may be restarting. No content is logged. */ }
    finally { inflight = false; }
  }
  setInterval(tick, 750);
  addEventListener('pagehide', () => { if (seen) { try { void chrome.runtime.sendMessage({ type: 'luna-state', state: {sequence: ++sequence, phase:'buffering'} }).catch(() => {}); } catch { /* Extension reload invalidated this document. */ } } });
  tick();
})();
