import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { useStoreProgress } from "./useStoreProgress";
const { invoke, callbacks } = vi.hoisted(() => ({ invoke: vi.fn(), callbacks: new Map<string, () => void>() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn(async (name, callback) => { callbacks.set(name, callback); return () => callbacks.delete(name); }) }));
let root: Root, host: HTMLDivElement;
function Harness({ id = "123", source = "gog" }) { const p = useStoreProgress({ id: source + ":" + id, store_id: id, source, name: "Game" }); return <span>{p?.minutes}:{p?.earned}/{p?.total}</span>; }
beforeEach(() => {
 vi.useFakeTimers(); vi.clearAllMocks(); callbacks.clear(); vi.spyOn(navigator,"onLine","get").mockReturnValue(true);
 (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
 host=document.createElement("div");document.body.append(host);root=createRoot(host);
});
afterEach(() => { act(() => root.unmount());host.remove();vi.restoreAllMocks();vi.useRealTimers(); });
it("shows saved activity before network and preserves it when refresh fails",async()=>{
 invoke.mockImplementation((_name,args)=>args.offline?Promise.resolve({minutes:9533,earned:12,total:54}):Promise.reject("offline"));
 await act(async()=>root.render(<Harness/>));expect(host.textContent).toBe("9533:12/54");
 expect(invoke.mock.calls.map(c=>c[1].offline)).toEqual([true,false]);
});
it("ignores late account/title replies and refreshes after a completed stream",async()=>{
 let resolve!:(v:unknown)=>void;
 invoke.mockImplementation((_name,args)=>args.gameId==="123"&&!args.offline?new Promise(r=>{resolve=r;}):Promise.resolve({minutes:10}));
 await act(async()=>root.render(<Harness/>));await act(async()=>root.render(<Harness id="456" source="epic"/>));
 await act(async()=>resolve({minutes:999}));expect(host.textContent).toBe("10:/");
 await act(async()=>callbacks.get("stream-session-ended")!());
 expect(invoke).toHaveBeenLastCalledWith("store_game_progress",{source:"epic",gameId:"456",offline:false,refresh:true});
});
it("stays local offline and never queries other store sources",async()=>{
 vi.spyOn(navigator,"onLine","get").mockReturnValue(false);invoke.mockResolvedValue({minutes:0});
 await act(async()=>root.render(<Harness/>));expect(invoke).toHaveBeenCalledTimes(1);expect(invoke.mock.calls[0][1].offline).toBe(true);
 await act(async()=>root.render(<Harness source="steam"/>));expect(invoke).toHaveBeenCalledTimes(1);expect(host.textContent).toBe(":/");
});
