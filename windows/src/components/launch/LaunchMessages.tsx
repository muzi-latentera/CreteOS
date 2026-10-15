import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

export const launchMessages = [
  "Contacting the mainframe…",
  "Asking the servers nicely…",
  "Waking the pixel gremlins…",
  "Untangling imaginary cables…",
  "Giving the pixels a pep talk…",
  "Checking behind the secret door…",
  "Polishing the virtual buttons…",
  "Borrowing a little moonlight…",
  "Making room for one more adventure…",
  "Negotiating with the loading bar…",
  "Rolling for a smooth landing…",
  "Summoning the good graphics…",
  "Shaking the digital snow globe…",
  "Sending a strongly worded packet…",
  "Fingers crossed. Controller ready.",
] as const;

export function LaunchMessages() {
  const { t } = useTranslation();
  const [first] = useState(() => Math.floor(Math.random() * launchMessages.length));
  const [step, setStep] = useState(0);
  useEffect(() => {
    const timer = window.setInterval(() => setStep(value => value + 1), 3500);
    return () => window.clearInterval(timer);
  }, []);
  const index = (first + step) % launchMessages.length;
  return <div data-launch-message="" style={{ padding: "18px 28px", maxWidth: "min(640px, 85vw)", textAlign: "center", color: "rgba(242,234,219,.88)", fontSize: "clamp(17px, 1.5vw, 24px)", lineHeight: 1.5, textShadow: "0 2px 14px rgba(0,0,0,.9)" }}>
    <style>{"@keyframes creteLaunchMessage { from { opacity:0; transform:translateY(3px) } to { opacity:1; transform:translateY(0) } } .crete-launch-message { animation:creteLaunchMessage .35s ease-out } @media(prefers-reduced-motion:reduce) { .crete-launch-message { animation:none } }"}</style>
    <span key={step} className="crete-launch-message" style={{ display: "inline-block" }}>{t(`launch.fun${index}`, { defaultValue: launchMessages[index] })}</span>
  </div>;
}
