import { expect, it, vi } from "vitest";
import { scrollPane } from "./smoothScroll";
it("UI Motion off skips Crete scroll animation without requiring OS reduced motion", () => {
 const host = document.createElement("div"); host.dataset.uiMotion = "off";
 const pane = document.createElement("div"); host.appendChild(pane);
 Object.defineProperties(pane, { scrollHeight:{value:1000}, clientHeight:{value:200}, scrollWidth:{value:400}, clientWidth:{value:200} });
 pane.scrollTo = vi.fn(); const raf = vi.fn(); vi.stubGlobal("requestAnimationFrame", raf);
 scrollPane(pane,{top:300});
 expect(pane.scrollTo).toHaveBeenCalledWith({top:300,left:0,behavior:"auto"});
 expect(raf).not.toHaveBeenCalled(); vi.unstubAllGlobals();
});
