import { useEffect, useRef, useState, type CSSProperties } from "react";
import { invoke } from "@tauri-apps/api/core";
export interface SteamChallenge { codeTypes: number[]; message: string }
export function SteamPasswordForm({ color, dim, error: sessionError, onStart }: { color: string; dim: string; error?: string; onStart?: () => void }) {
  const [account, setAccount] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [challenge, setChallenge] = useState<SteamChallenge | null>(null);
  const [codeType, setCodeType] = useState(2);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const input: CSSProperties = { width: "100%", boxSizing: "border-box", padding: "11px 12px", marginTop: 6, color, background: "rgba(128,128,128,.12)", border: "1px solid rgba(128,128,128,.5)", borderRadius: 8, fontSize: 15 };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (busy) return; setBusy(true); setError("");
    try {
      if (challenge && !sessionError) {
        const value = code; setCode("");
        await invoke("steam_guard_submit", { code: value, codeType });
      } else {
        setChallenge(null);
        onStart?.();
        const value = password; setPassword("");
        const result = await invoke<SteamChallenge>("steam_credentials_begin", { accountName: account, password: value });
        if (alive.current) { setChallenge(result); setCodeType(result.codeTypes[0] ?? 2); }
      }
    } catch (failure) { if (alive.current) setError(String(failure)); }
    finally { if (alive.current) setBusy(false); }
  };
  return <form onSubmit={submit} style={{ marginTop: 20, color }}>
    {!challenge || sessionError ? <>
      <label style={{ display: "block", marginBottom: 14 }}>Steam account name
        <input aria-label="Steam account name" name="username" autoComplete="username" autoCapitalize="none" spellCheck={false} value={account} onChange={e => setAccount(e.target.value)} disabled={busy} maxLength={128} required style={input} />
      </label>
      <label style={{ display: "block", marginBottom: 16 }}>Password
        <input aria-label="Password" name="password" type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} disabled={busy} maxLength={1024} required style={input} />
      </label>
    </> : <>
      <p role="status" style={{ color: dim, lineHeight: 1.5 }}>{challenge.message}</p>
      {challenge.codeTypes.length > 1 && <label>Verification method
        <select aria-label="Verification method" value={codeType} onChange={e => setCodeType(Number(e.target.value))} style={input}>{challenge.codeTypes.map(kind => <option key={kind} value={kind}>{kind === 2 ? "Email code" : "Authenticator code"}</option>)}</select>
      </label>}
      {challenge.codeTypes.length > 0 && <label style={{ display: "block", margin: "14px 0" }}>Steam Guard code
        <input aria-label="Steam Guard code" autoComplete="one-time-code" autoCapitalize="characters" spellCheck={false} value={code} onChange={e => setCode(e.target.value)} maxLength={5} minLength={5} required disabled={busy} style={input} />
      </label>}
    </>}
    {(error || sessionError) && <p role="alert" style={{ color: "#ff8d8d", lineHeight: 1.5 }}>{error || sessionError}</p>}
    {(!challenge || sessionError || challenge.codeTypes.length > 0) && <button type="submit" disabled={busy} style={{ ...input, cursor: busy ? "wait" : "pointer", fontWeight: 700, marginBottom: 12 }}>{busy ? "Connecting…" : challenge && !sessionError ? "Submit code" : "Sign in"}</button>}
    <p style={{ color: dim, fontSize: 12, lineHeight: 1.5 }}>Your password is not saved. Steam may still require an email code or authenticator verification.</p>
  </form>;
}
