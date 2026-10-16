import { useEffect, useRef } from "react";
import { listen } from "@tauri-apps/api/event";

/** Refresh store totals after a tracked session, independently of visible artwork. */
export function useStreamLibraryRefresh(refresh: (options: { background: true }) => unknown) {
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;
  useEffect(() => {
    let disposed = false;
    let lastSession = 0;
    let pending = false;
    let timer: ReturnType<typeof setTimeout>;
    let release: (() => void) | undefined;
    const update = () => {
      if (disposed || !pending || !navigator.onLine) return;
      pending = false;
      void Promise.resolve(refreshRef.current({ background: true })).catch(() => {});
      clearTimeout(timer);
      timer = setTimeout(() => {
        if (!disposed && navigator.onLine) void Promise.resolve(refreshRef.current({ background: true })).catch(() => {});
      }, 30_000);
    };
    void listen<{ session: number }>("stream-session-ended", event => {
      if (disposed || event.payload.session <= lastSession) return;
      lastSession = event.payload.session;
      pending = true;
      update();
    }).then(unlisten => { if (disposed) unlisten(); else release = unlisten; }).catch(() => {});
    window.addEventListener("online", update);
    return () => { disposed = true; clearTimeout(timer); release?.(); window.removeEventListener("online", update); };
  }, []);
}
