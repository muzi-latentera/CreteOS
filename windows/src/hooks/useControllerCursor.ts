import { useEffect } from "react";
import { restoreMouseCursor } from "../utils/inputMode";

export function useControllerCursor(): void {
  useEffect(() => {
    window.addEventListener("pointermove", restoreMouseCursor, { passive: true });
    window.addEventListener("pointerdown", restoreMouseCursor, { passive: true });
    return () => {
      window.removeEventListener("pointermove", restoreMouseCursor);
      window.removeEventListener("pointerdown", restoreMouseCursor);
      delete document.documentElement.dataset.controllerCursor;
    };
  }, []);
}
