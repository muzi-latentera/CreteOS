/** Shared launch-screen and game-page explanations. Unknown errors retain detail. */
export function streamErrorMessage(reason: unknown): string {
  const raw = String(reason).replace(/^Error:\s*/, "");
  const known: [RegExp, string][] = [
    [/MoonDeck Buddy is unavailable/i, "CreteOS lost contact with MoonDeck Buddy on Bosgame. Check both devices are connected, then retry. If Bosgame is reachable, check its MoonDeck Buddy service. This message alone does not identify a weak Wi-Fi signal."],
    [/did not authorize.*HTTP (401|403)/i, "Bosgame rejected CreteOS’s MoonDeck Buddy pairing. Pair MoonDeck Buddy again in Moonlight settings; Moonlight’s own pairing is separate."],
    [/does not support.*HTTP 404|Unsupported MoonDeck Buddy API/i, "Bosgame’s MoonDeck Buddy API is incompatible with this CreteOS build. Update Buddy before retrying."],
    [/Host query timed out/i, "Bosgame did not answer the SSH check in time. Check its network connection and SSH access, then retry. A busy host can also delay this check."],
    [/Moonlight closed or disconnected before the game started|Connection terminated.*-1/i, "The Moonlight connection to Bosgame ended before the game was ready. Check the connection and retry. Any game already started on Bosgame may still be running; this error does not prove it crashed."],
    [/streaming window closed before handoff/i, "The streaming client closed before CreteOS could show the game. Reopen the client to review its error, check the connection, then retry."],
    [/Game startup is taking longer/i, "Bosgame has not confirmed that the game started. Open Moonlight and check Steam for a sign-in, cloud-save, download or launch prompt. The stream is still monitored."],
    [/MoonDeckStream did not become ready/i, "Moonlight connected, but MoonDeckStream did not become ready on Bosgame. Check Sunshine and MoonDeck Buddy on Bosgame, then retry."],
    [/Buddy could not start Steam|Steam did not become ready/i, "Steam did not become ready on Bosgame’s streaming desktop. Open Moonlight and finish any Steam sign-in or update prompt, then retry."],
    [/Buddy could not launch this game/i, "MoonDeck Buddy rejected this game’s launch on Bosgame. Check it is installed and that Steam has no pending launch or save-sync prompt."],
    [/already streaming a different game/i, "Bosgame is already streaming another game. Quit that game or end its stream before launching this one. CreteOS has left the existing session running."],
    [/Close GeForce NOW before pressing Play|Please close GeForce Now/i, "GeForce NOW must be idle before its quality settings can change. Return to its home screen and retry; CreteOS will restart a recognised idle client automatically."],
    [/Unsupported streaming settings/i, "These streaming settings are not supported. Choose one of the listed resolutions, frame rates, bitrates and codecs, then retry."],
    [/underlying handle is not available|artwork.*unavailable|artwork HWND/i, "CreteOS could not prepare the loading-screen window. Return to CreteOS and retry; if it repeats, restart CreteOS. This does not establish a game crash."],
  ];
  return known.find(([pattern]) => pattern.test(raw))?.[1] ?? raw;
}
