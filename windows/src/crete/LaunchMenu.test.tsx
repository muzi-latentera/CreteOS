import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { invoke } from "@tauri-apps/api/core";
import { LaunchMenu } from "./LaunchMenu";
vi.mock("@tauri-apps/api/core",()=>({invoke:vi.fn(async(command:string)=>{
  if (command === "get_stream_network_status") return { wired: false };
  if (command === "get_local_display_modes") return { current: { width: 2560, height: 1600, hz: 144 }, modes: [{ width: 1920, height: 1200, hz: 60 }] };
  if (command === "get_stream_audio_devices") return { output: [{ id: "headphones", name: "Arctis Nova 7" }], input: [{ id: "mic", name: "Arctis mic" }], defaultOutput: null, defaultInput: null };
  return { moonlight: "automatic", luna: "automatic", ps5RemotePlay: "automatic" };
})}));
vi.mock("@tauri-apps/api/event",()=>({listen:vi.fn(async()=>()=>{})}));
vi.mock("./text",()=>({useCreteText:()=>(label:string)=>label}));
vi.mock("../utils/gamepad",()=>({getBestGamepad:()=>null,readGpState:()=>({})}));
let root:Root; let host:HTMLDivElement;
beforeEach(()=>{Element.prototype.scrollIntoView=vi.fn();(globalThis as {IS_REACT_ACT_ENVIRONMENT?:boolean}).IS_REACT_ACT_ENVIRONMENT=true;});
afterEach(async()=>{await act(async()=>root?.unmount());host?.remove();vi.clearAllMocks();});
async function render(overrides:Record<string,unknown>={}, menuOpen=false, restorePlayFocus?:()=>void, localSetup?: Parameters<typeof LaunchMenu>[0]["localSetup"]) {
  const profile={host:"Bosgame",resolution:"2560x1600",fps:90,bitrate:80000,codec:"auto"};
  const streaming={display:{profile},geforceDisplay:{profile},configure:true,geforceConfigure:false,asobiConfigure:null,asobiGames:[],apps:["MoonDeckStream","Desktop"],steamId:"123",moonlightGame:null,buddy:{paired:true},hostInstalled:true,online:true,pcGame:true,clients:{moonlight:true},error:null,hostError:null,checking:false,setConfigure:vi.fn(),setGeforceConfigure:vi.fn(),setAsobiConfigure:vi.fn(),stream:vi.fn(async()=>{}),geforceStream:vi.fn(async()=>{}),startAsobi:vi.fn(async()=>{}),refresh:vi.fn(),...overrides};
  host=document.createElement("div");document.body.append(host);root=createRoot(host);
  await act(async()=>root.render(<LaunchMenu streaming={streaming as unknown as Parameters<typeof LaunchMenu>[0]["streaming"]} open={menuOpen} setOpen={()=>{}} local={()=>{}} localLabel="Local" localEnabled manage={()=>{}} manageLabel="Manage" block={()=>{}} restorePlayFocus={restorePlayFocus} localSetup={localSetup}/>));
  return streaming;
}
async function click(el:Element|null){expect(el).not.toBeNull();await act(async()=>(el as HTMLElement).click());}
it("keeps host tools out of the default view and retains the exact launch profile",async()=>{
  const s=await render();
  expect(document.querySelector('[aria-label="Moonlight stream settings"]')).not.toBeNull();
  expect(document.querySelector('[aria-label="Resolution"]')).toBeNull();
  expect(document.querySelector('select[aria-label="Display preset"]')).toBeNull();
  expect(document.body.textContent).not.toContain("Refresh host");
  expect(document.querySelector(".crete-stream-heading .crete-stream-summary")?.textContent).toBe("2560 × 1600, 90 FPS, 80 Mbps");
  await click(document.querySelector('[data-launch-primary]'));
  expect(s.stream).toHaveBeenCalledWith("MoonDeckStream",s.display.profile,{input:null,output:null});
  expect(invoke).not.toHaveBeenCalledWith("set_stream_wifi_preference",expect.anything());
});
it("opens a pointer choice list and cancels without changing the saved preference",async()=>{
  const s=await render();const select=document.querySelector<HTMLSelectElement>('[aria-label="G1 connection"]')!;
  await act(async()=>select.dispatchEvent(new MouseEvent("pointerdown",{bubbles:true,cancelable:true})));
  expect(document.querySelector('[role="listbox"]')).not.toBeNull();
  await act(async()=>window.dispatchEvent(new KeyboardEvent("keydown",{key:"Escape",bubbles:true})));
  expect(document.querySelector('[role="listbox"]')).toBeNull();
  expect(document.activeElement).toBe(select);
  expect(s.setConfigure).not.toHaveBeenCalledWith(false);
  expect(invoke).not.toHaveBeenCalledWith("set_stream_wifi_preference",expect.anything());
});
it("PlayStation keeps the exported ASOBI route and primary action",async()=>{
  const s=await render({configure:false,asobiConfigure:"ps5RemotePlay",asobiGames:[{provider:"ps5RemotePlay",id:"actual-shortcut",label:"PS5 game"}]});
  expect(document.querySelector('[aria-label="PS5 Remote Play settings"]')).not.toBeNull();
  await click(document.querySelector('[data-launch-primary]'));
  expect(s.startAsobi).toHaveBeenCalledWith("actual-shortcut",{input:null,output:null},null);
  expect(s.stream).not.toHaveBeenCalled();
});

it("saves local network choice and hands display and sound overrides to the app launch", async () => {
  const start = vi.fn();
  await render({ configure: false }, false, undefined, { name: "ASOBI", provider: "local", onClose: vi.fn(), onStart: start });
  const choose = async (field: string, value: string) => {
    const select = document.querySelector<HTMLSelectElement>(`select[aria-label="${field}"]`)!;
    await act(async () => select.dispatchEvent(new MouseEvent("pointerdown", { bubbles: true, cancelable: true })));
    await click(Array.from(document.querySelectorAll('[role="option"]')).find(option => option.textContent?.includes(value))!);
  };
  await choose("Home connection", "Hyperoptic Extra");
  expect(invoke).toHaveBeenCalledWith("set_stream_wifi_preference", { provider: "local", choice: "gaming" });
  await choose("Display mode", "1920 × 1200");
  await click(Array.from(document.querySelectorAll("button")).find(button => button.textContent === "Sound")!);
  await choose("Audio output", "Arctis Nova 7");
  await click(document.querySelector("[data-launch-primary]"));
  expect(start).toHaveBeenCalledWith({ audio: { input: null, output: "headphones" }, display: { width: 1920, height: 1200, hz: 60 } });
});

it("passes separate output and microphone choices to a stream",async()=>{
  const s=await render();
  await click(Array.from(document.querySelectorAll("button")).find(button=>button.textContent==="Sound")!);
  for(const [label, option] of [["Audio output", "Arctis Nova 7"], ["Microphone", "Arctis mic"]]){
    const select=document.querySelector<HTMLSelectElement>(`select[aria-label="${label}"]`)!;
    await act(async()=>select.dispatchEvent(new MouseEvent("pointerdown",{bubbles:true,cancelable:true})));
    await click(Array.from(document.querySelectorAll('[role="option"]')).find(item=>item.textContent?.includes(option))!);
  }
  await click(document.querySelector('[data-launch-primary]'));
  expect(s.stream).toHaveBeenCalledWith("MoonDeckStream",s.display.profile,{input:"mic",output:"headphones"});
});
it("Luna connection settings never expose or start a host stream",async()=>{
  await render({configure:false,provider:"luna"},true);
  await click(Array.from(document.querySelectorAll('[role="menuitem"]')).find(el=>el.textContent?.includes("Amazon Luna Wi-Fi"))!);
  expect(document.querySelector('[aria-label="Amazon Luna Wi-Fi"]')).not.toBeNull();
  expect(document.querySelector('[data-launch-primary]')).toBeNull();
  expect(document.body.textContent).not.toContain("Stream options");
});

it("restores the Play action after a launch error and returns focus on cancel",async()=>{
  const restore=vi.fn();
  const s=await render({error:"Bosgame could not verify Steam."},false,restore);
  const play=document.querySelector<HTMLButtonElement>("[data-launch-primary]")!;
  await act(async()=>{await new Promise(resolve=>setTimeout(resolve,35));});
  expect(document.activeElement).toBe(play);
  await act(async()=>window.dispatchEvent(new KeyboardEvent("keydown",{key:"Escape",bubbles:true})));
  expect(s.setConfigure).toHaveBeenCalledWith(false);
  expect(restore).toHaveBeenCalledOnce();
});
