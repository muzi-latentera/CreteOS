export function noteControllerInput(state: Record<string, boolean>): void {
  if (typeof document !== "undefined" && Object.values(state).some(Boolean)) {
    document.documentElement.dataset.controllerCursor = "hidden";
  }
}

export function restoreMouseCursor(event: PointerEvent): void {
  if (event.pointerType === "mouse") delete document.documentElement.dataset.controllerCursor;
}
