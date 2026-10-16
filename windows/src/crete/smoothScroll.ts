const animations = new WeakMap<HTMLElement, { frame: number }>();

/** A finger drag or wheel owns the pane immediately, including its momentum. */
export function cancelPaneScroll(element: HTMLElement) {
  const animation = animations.get(element);
  if (animation) cancelAnimationFrame(animation.frame);
  animations.delete(element);
}

/** Short, interruptible scrolling confined to one pane; never scroll ancestors. */
export function scrollPane(element: HTMLElement, destination: { top?: number; left?: number }) {
  const previous = animations.get(element);
  if (previous) cancelAnimationFrame(previous.frame);
  const top = Math.max(0, Math.min(element.scrollHeight - element.clientHeight, destination.top ?? element.scrollTop));
  const left = Math.max(0, Math.min(element.scrollWidth - element.clientWidth, destination.left ?? element.scrollLeft));
  if (element.closest('[data-ui-motion="off"]') || window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) {
    element.scrollTo({ top, left, behavior: "auto" });
    animations.delete(element);
    return;
  }
  const startTop = element.scrollTop;
  const startLeft = element.scrollLeft;
  const start = performance.now();
  const animation = { frame: 0 };
  const tick = (now: number) => {
    const progress = Math.min(1, (now - start) / 190);
    const eased = 1 - Math.pow(1 - progress, 3);
    element.scrollTo({ top: startTop + (top - startTop) * eased, left: startLeft + (left - startLeft) * eased, behavior: "auto" });
    if (progress < 1 && element.isConnected) animation.frame = requestAnimationFrame(tick);
    else animations.delete(element);
  };
  animations.set(element, animation);
  animation.frame = requestAnimationFrame(tick);
}
