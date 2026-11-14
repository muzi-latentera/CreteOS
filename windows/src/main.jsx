import React from "react";
import ReactDOM from "react-dom/client";
import "./i18n";
import "./styles/motion.css";
import "./styles/input-mode.css";
import "./theme/crete.css";
import "./crete/crete.css";
import App from "./App";
import { StreamArtworkWindow } from "./StreamArtworkWindow";
import { AppCrashScreen, ErrorBoundary, recordFrontendError } from "./components/ErrorBoundary";

// Errors outside rendering (timers, promises) are logged; they don't blank the app.
window.addEventListener("error", event => recordFrontendError("window", event.error ?? event.message));
window.addEventListener("unhandledrejection", event => recordFrontendError("promise", event.reason));

ReactDOM.createRoot(document.getElementById("root")).render(
    new URLSearchParams(location.search).has("stream_artwork")
        ? <StreamArtworkWindow />
        : <ErrorBoundary area="app" fallback={() => <AppCrashScreen />}><App /></ErrorBoundary>
);
