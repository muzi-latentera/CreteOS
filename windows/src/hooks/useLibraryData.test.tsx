import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { useLibraryData } from "./useLibraryData";
import type { App } from "../types";

const { invoke } = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
let root: Root;
let host: HTMLDivElement;
let library: ReturnType<typeof useLibraryData>;
let release: (apps: App[]) => void;
let hold = false;
const original = { id: "one", name: "First game", app_type: "game" } as App;
function Harness() {
  library = useLibraryData({ fetchGameArt: () => {} });
  return <span>{library.apps[0]?.name}</span>;
}
beforeEach(async () => {
  vi.useFakeTimers();
  hold = false;
  invoke.mockReset().mockImplementation((command: string) => {
    if (command === "get_all_apps") return hold ? new Promise<App[]>(resolve => { release = resolve; }) : Promise.resolve([original]);
    return Promise.resolve([]);
  });
  (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
  host = document.createElement("div"); document.body.append(host);
  root = createRoot(host);
  await act(async () => root.render(<Harness />));
  hold = true;
});
afterEach(() => {
  act(() => root.unmount()); host.remove(); vi.useRealTimers();
});
it("updates in the background without a blocking scan and joins concurrent refreshes", async () => {
  let pending!: Promise<void>;
  act(() => { pending = library.refreshLibrary({ background: true }); });
  expect(library.libraryRefreshStatus).toBeNull();
  expect(host.textContent).toBe("First game");
  expect(library.refreshLibrary({ background: true })).toBe(pending);
  expect(invoke.mock.calls.filter(([command]) => command === "get_all_apps")).toHaveLength(2);
  await act(async () => { release([{ ...original, name: "Updated game" }]); await pending; });
  expect(host.textContent).toBe("Updated game");
  expect(library.libraryRefreshStatus).toBeNull();
});

it("unpins retained copies through the preferred tile without changing ownership", async () => {
  act(() => { library.pinsRef.current = ["other-copy"]; });
  invoke.mockImplementation(async (command: string, args?: {appId?: string}) => {
    if(command === "toggle_pin") { expect(args?.appId).toBe("other-copy"); return []; }
    return [];
  });
  await act(async () => { library.togglePin({...original,library_variants:[{...original,id:"other-copy"}]}); await Promise.resolve(); await Promise.resolve(); });
  expect(library.pinsRef.current).toEqual([]);
  expect(library.allAppsRef.current).toEqual([original]);
});
it("retains progress for an explicit manual refresh", async () => {
  let pending!: Promise<void>;
  act(() => { pending = library.refreshLibrary(); });
  expect(library.libraryRefreshStatus).toBe("scanning");
  await act(async () => { release([original]); await pending; });
  expect(library.libraryRefreshStatus).toBe("done");
});
