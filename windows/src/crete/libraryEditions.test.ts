import { expect, it } from "vitest";
import type { App } from "../types";
import { preferGameEditions } from "./libraryEditions";
const game = (id: string, name: string, source: string): App => ({ id, name, source, app_type: "game", installed: false });
it("prefers remastered over original and Steam for the same edition", () => {
 const apps = [game("a","BioShock 2","steam"),game("b","BioShock 2 Remastered","epic"),game("c","BioShock 2 Remastered","steam")];
 const result=preferGameEditions(apps); expect(result.map(app=>app.id)).toEqual(["c"]); expect(result[0].library_variants?.map(app=>app.id).sort()).toEqual(["a","b"]); expect(apps).toHaveLength(3);
});
it("prefers Complete on Epic over standard Steam and Steam over Xbox on ties", () => {
 expect(preferGameEditions([game("a","BioShock Infinite","steam"),game("b","BioShock Infinite: Complete Edition","epic")])[0].id).toBe("b");
 expect(preferGameEditions([game("a","BioShock Infinite","xbox"),game("b","BioShock Infinite","steam")])[0].id).toBe("b");
});
it("keeps sequels, years, remakes and ambiguous same-store releases separate", () => {
 const names=["BioShock","BioShock 2","Resident Evil 4 (2005)","Resident Evil 4","Final Fantasy VII","Final Fantasy VII Remake","Prey","PREY"];
 expect(preferGameEditions(names.map((name,i)=>game(String(i),name,"steam")))).toHaveLength(names.length);
});
it("retains installed paths and other-copy history without adding playtimes together", () => {
 const a={...game("a","Game","steam"),installed:true,launch_path:"C:/game.exe",playtime_minutes:12};
 const b={...game("b","Game: Complete Edition","epic"),playtime_minutes:9};
 const result=preferGameEditions([a,b],[{...a,launched_at:1700000000}]);
 expect(result[0].id).toBe("b"); expect(result[0].last_played).toBe(1700000000000); expect(result[0].playtime_minutes).toBe(9); expect(result[0].library_variants?.[0].launch_path).toBe("C:/game.exe");
});
