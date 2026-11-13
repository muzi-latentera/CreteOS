import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { getBestGamepad, readGpState, type GpState } from "../../utils/gamepad";
import ModalShell from "./ModalShell";
import { useTheme } from "../../contexts/ThemeContext";

interface Props {
  message: string;
  confirmLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
  zIndex?: number;
}

export default function ConfirmModal({ message, confirmLabel, onConfirm, onCancel, zIndex = 2500 }: Props) {
  const { glass, accent, theme, isDark } = useTheme();
  const { t } = useTranslation();
  const label = confirmLabel ?? t("confirm.yes");

  useEffect(() => {
    const last: Partial<GpState> = {};
    let rafId: number;
    let suppressFrames = 20;
    const poll = () => {
      const gp = getBestGamepad();
      if (suppressFrames > 0) {
        suppressFrames--;
        if (gp) Object.assign(last, readGpState(gp));
        rafId = requestAnimationFrame(poll);
        return;
      }
      if (gp) {
        const state = readGpState(gp);
        if (state.Enter  && !last.Enter)  { onConfirm(); }
        if (state.Escape && !last.Escape) { onCancel(); }
        Object.assign(last, state);
      }
      rafId = requestAnimationFrame(poll);
    };
    rafId = requestAnimationFrame(poll);
    const key = (event: KeyboardEvent) => {
      if (event.key !== "Enter" && event.key !== "Escape") return;
      event.preventDefault(); event.stopImmediatePropagation();
      if (event.key === "Enter") onConfirm(); else onCancel();
    };
    window.addEventListener("keydown", key, true);
    return () => { cancelAnimationFrame(rafId); window.removeEventListener("keydown", key, true); };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const shortcuts = [
    { btn: "A", label },
    { btn: "B", label: t("common.cancel") },
  ];

  return (
    <div data-owns-input="">
      <ModalShell
        title={message}
        shortcuts={shortcuts}
        width={380}
        zIndex={zIndex}
      />
    </div>
  );
}
