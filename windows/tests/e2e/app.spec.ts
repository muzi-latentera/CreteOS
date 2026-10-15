import { expect, test, type Page } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    let callbackId = 0;
    let listenerId = 0;
    const params = new URLSearchParams(window.location.search);
    const counts: Record<string, number> = {};
    let pinnedIds: string[] = params.has("parity") ? ["steam://rungameid/621"] : [];
    (window as any).__invokeCounts = counts;

    const emptyArrays = new Set([
      "get_all_apps",
      "get_hidden",
      "get_pins",
      "get_recents",
      "get_recent_games",
      "get_app_collections",
      "get_game_collections",
      "get_running_launched",
    ]);

    const responses: Record<string, unknown> = {
      get_screen_resolution: { width: 1920, height: 1080 },
      get_settings: { default_tab: "Home", animated_heroes: "static" },
      get_app_memberships: {},
      get_game_memberships: {},
      get_custom_data: { apps: [], folders: [] },
      get_custom_art: {},
      get_custom_categories: {},
      get_battery: { percent: 80, charging: false },
      get_cached_art_bulk: {},
      steam_account_status: { connected: false, owned_count: 0 },
      xbox_account_status: { connected: false, owned_count: 0 },
      spotify_status: { connected: true, client_id_set: true, product: "premium" },
      spotify_playback_state: {
        item: {
          id: "track-1",
          name: "Test Track",
          artists: [{ name: "Test Artist" }],
          album: { name: "Test Album", images: [] },
          duration_ms: 180_000,
        },
        progress_ms: 30_000,
        is_playing: false,
        shuffle_state: false,
        repeat_state: "off",
      },
      spotify_playlists: { items: [] },
      spotify_devices: { devices: [] },
      get_system_volume: { percent: 45, muted: false },
      get_brightness: 40,
      ensure_lofi_media: "C:\\Users\\test\\AppData\\Local\\CreteOS\\media\\lofi\\lofi_dog.mp4",
      get_storage_info: Array.from({ length: 8 }, (_, index) => ({ mountPoint: `${String.fromCharCode(67 + index)}:/`, label: index === 0 ? "System" : "Games", totalBytes: 1000000000000, freeBytes: 400000000000, isDefaultInstallDrive: index === 0 })),
      "plugin:window|is_focused": true,
    };

    const gamepadButtons = Array.from({ length: 16 }, () => ({ pressed: false, touched: false, value: 0 }));
    const gamepad = { mapping: "standard", axes: [0, 0, 0, 0], buttons: gamepadButtons };
    (window as any).__setGamepadAxis = (axis: number, value: number) => { gamepad.axes[axis] = value; };
    (window as any).__setGamepadButton = (index: number, pressed: boolean) => {
      gamepadButtons[index] = { pressed, touched: pressed, value: pressed ? 1 : 0 };
    };
    Object.defineProperty(navigator, "getGamepads", {
      configurable: true,
      value: () => [gamepad],
    });

    window.fetch = async () => new Response("[]", {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });

    (window as any).__TAURI_EVENT_PLUGIN_INTERNALS__ = {
      unregisterListener: () => {},
    };
    (window as any).__TAURI_INTERNALS__ = {
      metadata: { currentWindow: { label: "main" } },
      convertFileSrc: (path: string) => `http://asset.localhost/${encodeURIComponent(path)}`,
      unregisterCallback: () => {},
      transformCallback: (callback: unknown) => {
        callbackId += 1;
        (window as any)[`_${callbackId}`] = callback;
        return callbackId;
      },
      invoke: async (command: string, args?: any) => {
        counts[command] = (counts[command] || 0) + 1;
        if (command === "plugin:event|listen") {
          listenerId += 1;
          return listenerId;
        }
        if (command === "plugin:event|unlisten" || command === "plugin:event|emit") return null;
        if (command === "save_settings") { (window as any).__savedSettings = args.settings; return null; }
        if (command === "get_build_identity") return {version:"0.1.0",sourceCommit:"fixturecommit"};
        if (command === "launch_streaming_client") { (window as any).__lastStreamingClient = args; return null; }
        if (params.has("newsAsset") && command === "get_crete_news_rss") return '<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Cached Games article</title><link href="https://www.reddit.com/r/Games/comments/cached"/><content type="html">&lt;a href="https://www.eurogamer.net/test"&gt;[link]&lt;/a&gt;</content></entry></feed>';
        if (params.has("newsAsset") && command === "get_crete_article_thumbnail") return "C:/Users/Muzi/AppData/Local/CreteOS/media/news/fixture.jpg";
        if (command === "get_pins") return pinnedIds;
        if (command === "toggle_pin") { const id = args.appId; pinnedIds = pinnedIds.includes(id) ? pinnedIds.filter(value => value !== id) : [...pinnedIds, id]; return pinnedIds; }
        if (params.has("parity") && command === "get_game_collections") return [{ id: "favourites", name: "Favourites" }];
        if (params.has("parity") && command === "get_game_memberships") return { "steam://rungameid/620": ["favourites"], "steam://rungameid/621": ["favourites"] };
        if (params.has("sizes") && command === "get_install_size") return 1073741824;
        if (params.has("sizes") && command === "get_steam_download_size") return 2147483648;
        if (command === "launch_moonlight") { if (params.has("streamOffline")) throw new Error("Host unavailable"); (window as any).__lastStream = args; return null; }
        if (command === "get_host_installed_games") { if (params.has("holdHost")) await new Promise<void>(resolve => { (window as any).__releaseHostCheck = resolve; }); if (params.has("inventoryOffline")) throw new Error("SSH unavailable"); return params.has("hostInstalled") ? [{ steamAppId: "870780", name: "Control Ultimate Edition" }] : []; }
        if (command === "get_moondeck_status") return { paired: !params.has("buddyUnpaired"), version: 8 };
        if (command === "get_moonlight_profile") return { displayKey: "DISPLAY1:1920x1080", profile: { host: "Bosgame", resolution: "1920x1080", fps: 120, bitrate: 80000, codec: "auto" } };
        if (command === "get_moonlight_apps") { if (params.has("hostOffline")) throw new Error("Host offline"); return params.has("hostGame") ? ["Control Ultimate Edition", "Desktop"] : ["Desktop", "Steam Big Picture", "MoonDeckStream"]; }
        if (command === "get_streaming_clients") return { geforceNow: true, moonlight: true };
        if (command === "wait_stream_window" && params.has("holdStreamWindow")) return new Promise<void>(resolve => { (window as any).__releaseStreamWindow = resolve; });
        if (command === "get_geforce_profile" && params.has("holdGfnProfile")) await new Promise<void>(resolve => { (window as any).__releaseGfnProfile = resolve; });
        if (command === "get_geforce_profile") return { displayKey: "DISPLAY1:1920x1080", profile: { host: "GeForce NOW", resolution: "1920x1080", fps: 120, bitrate: 80000, codec: "auto" } };
        if (command === "start_geforce_game" || command === "launch_geforce_game") { (window as any).__lastGeforce = args; return null; }
        if (command === "get_crete_review_videos" && params.has("manyMedia")) return ["Whitelight", "Whitelight", "Skill Up", "Joseph Anderson", "Noah Caldwell-Gervais", "gameranx"].map((channel, i) => ({ id: `review${i}`, title: `Review ${i}`, channel, thumbnail: "https://fixture.test/review.svg", url: `https://www.youtube.com/watch?v=review${i}` }));
        if (command === "get_crete_review_videos") return params.has("xboxDetails") ? [{ id: "abcdefghijk", title: "A Plague Tale Requiem analysis", channel: "Whitelight", thumbnail: "https://fixture.test/review.svg", url: "https://www.youtube.com/watch?v=abcdefghijk" }, { id: "lmnopqrstuv", title: "A Plague Tale Requiem review", channel: "Skill Up", thumbnail: "https://fixture.test/review.svg", url: "https://www.youtube.com/watch?v=lmnopqrstuv" }] : [];
        if (command === "get_xbox_game_progress") return { minutes: 922, earned: 20, total: null, gamerscore: 330, totalGamerscore: 1000 };
        if (command === "fetch_title_store_metadata") return { source: "xbox", appId: "2089483628", developers: ["Asobo Studio"], publishers: ["Focus Entertainment"], shortDescription: "A Plague Tale: Requiem description", aboutHtml: "", genres: [], screenshots: [], movies: [], controllerSupport: "none", fetchedAt: 0 };
        if (command === "get_crete_news_rss") return params.has("news") ? `<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Test gaming headline</title><link href="https://www.reddit.com/r/Games/comments/test"/></entry></feed>` : "";
        if (params.has("applications") && command === "get_app_collections") return [{ id: "tools", name: "Tools" }];
        if (params.has("applications") && command === "get_app_memberships") return { "app:terminal": ["tools"] };
        if (command === "get_settings") {
          const bottombarMode = new URLSearchParams(window.location.search).get("barMode") || "smart";
          return { ...(responses.get_settings as Record<string, unknown>), bottombar_mode: bottombarMode,
            onboarding_complete: params.get("fresh") !== "true", theme: params.get("theme") || (params.has("crete") ? "crete" : "space"),
            accent: "crete", surface_style: params.get("surface") || "glass", ui_motion: params.has("motion"), ui_scale: 1, language: params.get("lang") || "en", app_list_view: params.has("listApps"), app_list_cols: 2,
            show_home_collections: params.has("parity"), show_home_collection_names: true, home_pinned_pos: params.has("parity") ? "top" : "none", home_mode: params.get("homeMode") || "semi", animated_heroes: params.has("animated") ? "animated" : "static",
            home_launch_games_directly: params.has("direct") };
        }
        if (command === "get_all_apps") {
          if (params.has("lunaDetails")) return [{ id: "cloud:luna:dispatch", name: "Dispatch", last_played: params.has("played") ? 1725000000 : undefined, source: "cloud", app_type: "game", installed: true, steam_appid: 2592160, launch_path: "https://luna.amazon.co.uk/play?asin=B0H6GQJMBN" }, { id: "cloud:luna:galactic", name: "STAR WARS: Galactic Racer", source: "cloud", app_type: "game", installed: true, steam_appid: 4078430, launch_path: "https://luna.amazon.co.uk/play?asin=B0HLN4GG5K" }];
          if (params.has("xboxDetails")) return [{ id: "xbox-owned:2089483628", name: "A Plague Tale: Requiem", source: "xbox", app_type: "game", xbox_product_id: "2089483628", installed: false }];
          if (params.has("applications")) return [
            { id: "app:terminal", name: "Terminal", launch_path: "C:\\Windows\\Terminal.exe", app_type: "app", installed: true },
            { id: "app:browser", name: "Browser", launch_path: "C:\\Apps\\Browser.exe", app_type: "app", installed: true },
          ];
          if (params.has("holdRefresh") && counts[command] > 1) await new Promise((resolve) => { (window as any).__releaseScan = resolve; });
          if (params.has("rich")) return Array.from({ length: 24 }, (_, i) => ({
            id: `steam://rungameid/${620 + i}`, name: i === 0 ? (params.has("gfnHero") ? "Control Ultimate Edition" : "Portal 2") : i === 1 ? "Hollow Knight" : i === 2 && params.has("gfn") ? "Control Ultimate Edition" : `Test Game ${String(i + 1).padStart(2, "0")}`,
            app_type: "game", launch_path: i === 23 ? "https://example.test/play" : `steam://rungameid/${620 + i}`, source: i === 23 ? "cloud" : "steam", steam_appid: (i === 2 && params.has("gfn")) || (i === 0 && params.has("gfnHero")) ? 870780 : 620 + i,
            installed: i === 0 && params.has("gfnHero") ? false : i === 2 && params.has("gfnLocal") ? true : i % 3 !== 2, last_played: params.has("played") ? 1725000000 - i * 86400 : undefined,
            playtime_minutes: i === 0 ? 93 : undefined,
          }));
          return params.has("catalog") ? [{ id: "steam://rungameid/620", name: "Portal 2", app_type: "game", installed: true }] : [];
        }
        if (params.has("rich") && command === "fetch_game_art") return { grid: "https://fixture.test/cover.svg", hero_static: "https://fixture.test/hero.svg", hero_animated: params.has("animated") ? "https://fixture.test/hero.mp4" : undefined };
        if (params.has("lunaDetails") && command === "fetch_store_metadata") { (window as any).__lastStoreMeta = args; return { source: "steam", appId: args.appId, developers: ["Luna Info Developer"], publishers: ["Luna Info Publisher"], shortDescription: "Cloud game informational description", screenshots: [], movies: [], genres: [], aboutHtml: "", controllerSupport: "full", fetchedAt: Date.now() }; }
        if (params.has("rich") && command === "fetch_store_metadata") return {
          source: "steam", appId: "620", shortDescription: "Test catalogue description. This fixture verifies the Windows layout and metadata pipeline.",
          aboutHtml: "", developers: ["Test Developer"], publishers: ["Test Publisher"], genres: [],
          screenshots: [], movies: params.has("manyMedia") ? Array.from({ length: 6 }, (_, i) => ({ id: i, name: `Trailer ${i}`, thumbnail: "https://fixture.test/review.svg", mp4: "https://fixture.test/trailer.mp4" })) : [], controllerSupport: "full", fetchedAt: Date.now(),
        };
        if (params.has("rich") && command === "get_cached_art_bulk") return Object.fromEntries(Array.from({ length: 24 }, (_, i) => [
          i === 0 ? "Portal 2" : i === 1 ? "Hollow Knight" : `Test Game ${String(i + 1).padStart(2, "0")}`,
          { grid: "https://fixture.test/cover.svg", hero_static: "https://fixture.test/hero.svg", hero_animated: params.has("animated") ? "https://fixture.test/hero.mp4" : undefined },
        ]));
        if (params.has("connectedAccounts") && command === "steam_account_status") {
          return { connected: true, account_name: "Steam Tester", owned_count: 12 };
        }
        if (params.has("connectedAccounts") && command === "xbox_account_status") {
          return { connected: true, gamertag: "Microsoft Tester", owned_count: 8 };
        }
        if (emptyArrays.has(command)) return [];
        if (Object.prototype.hasOwnProperty.call(responses, command)) return responses[command];
        return null;
      },
    };
  });
});

test("boots the Home shell with mocked Tauri commands", async ({ page }) => {
  const pageErrors: Error[] = [];
  page.on("pageerror", (error) => pageErrors.push(error));

  await page.goto("/?barMode=smart");

  await expect(page.getByRole("button", { name: "Home", exact: true })).toBeVisible({ timeout: 10_000 });
  await expect(page.locator("#root")).not.toBeEmpty();

  await expect(page.getByRole("img", { name: "MENU button" })).toBeVisible();
  await page.evaluate(() => (window as any).__setGamepadButton(9, true));
  await expect(page.getByText("Helper", { exact: true })).toBeVisible();
  await page.evaluate(() => (window as any).__setGamepadButton(9, false));
  await expect(page.getByText("Test Track", { exact: true })).toBeVisible();
  await expect(page.getByText(/^Volume/)).toBeVisible();
  await expect(page.getByText(/^Brightness/)).toBeVisible();
  await expect(page.getByRole("button", { name: /Controls/ })).toBeVisible();

  const tray = page.locator('[data-modal="helper"]');
  const seekSlider = tray.locator('input[type="range"][max="180000"]');
  const systemSliders = tray.locator('input[type="range"][max="100"]');
  await expect(seekSlider).toHaveValue("30000");
  await expect(systemSliders).toHaveCount(2);
  await expect(systemSliders.nth(0)).toHaveValue("45");
  await expect(systemSliders.nth(1)).toHaveValue("40");

  const settingsBox = await tray.getByRole("button", { name: "Settings" }).boundingBox();
  const volumeBox = await tray.getByText(/^Volume/).boundingBox();
  const spotifyBox = await tray.getByText("Test Track", { exact: true }).boundingBox();
  expect(settingsBox?.y).toBeLessThan(volumeBox?.y ?? 0);
  expect(volumeBox?.y).toBeLessThan(spotifyBox?.y ?? 0);

  const trayHeight = await tray.evaluate((element) => element.getBoundingClientRect().height);

  await tray.getByRole("button", { name: "Settings", exact: true }).hover();
  await page.mouse.move(0, 0);
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("ArrowRight");
  await page.keyboard.press("ArrowRight");
  await page.keyboard.press("ArrowRight");
  await page.keyboard.press("ArrowRight");
  await expect(seekSlider).toHaveValue("30000");
  expect(await tray.evaluate((element) => element.getBoundingClientRect().height)).toBeCloseTo(trayHeight, 1);

  // Select the slider explicitly before testing its adjustment contract.
  await seekSlider.hover();
  await page.mouse.move(0, 0);
  await expect(seekSlider.locator("..")).toHaveCSS("outline-color", "rgb(233, 169, 60)");
  await page.keyboard.press("ArrowRight");
  await expect(seekSlider).toHaveValue("30000");
  await seekSlider.hover();
  await page.mouse.move(0, 0);
  await page.keyboard.press("Enter");
  await expect(seekSlider.locator("..")).toContainText("Done");
  await page.keyboard.press("ArrowRight");
  await expect(seekSlider).toHaveValue("40000");
  await page.keyboard.press("Enter");

  await systemSliders.nth(0).hover();
  await page.mouse.move(0, 0);
  await page.keyboard.press("ArrowLeft");
  await expect(systemSliders.nth(0)).toHaveValue("45");
  await expect(systemSliders.nth(1)).toHaveValue("40");
  expect(await tray.evaluate((element) => element.getBoundingClientRect().height)).toBeCloseTo(trayHeight, 1);

  await page.keyboard.press("Enter");
  await page.keyboard.press("ArrowLeft");
  await expect(systemSliders.nth(0)).toHaveValue("40");
  await page.keyboard.press("Escape");
  await page.keyboard.press("ArrowRight");
  await expect(systemSliders.nth(0)).toHaveValue("40");
  expect(pageErrors).toEqual([]);
});

test("account rows require action focus before controller activation", async ({ page }) => {
  await page.goto("/?connectedAccounts=true");
  await page.getByText("Settings", { exact: true }).first().click();
  await frames(page, 20);

  await pad(page, 7);
  await frames(page, 20);
  const focusedRow = page.locator("[data-settings-row].focused");

  await pad(page, 13);
  await expect(focusedRow).toContainText("Steam");
  await pad(page, 15);
  expect(await page.evaluate(() => (window as any).__invokeCounts.steam_logout || 0)).toBe(0);

  for (let index = 0; index < 3; index++) await pad(page, 13);
  await expect(focusedRow).toContainText("Microsoft");
  await pad(page, 15);
  expect(await page.evaluate(() => (window as any).__invokeCounts.xbox_disconnect || 0)).toBe(0);
  await pad(page, 0);
  expect(await page.evaluate(() => (window as any).__invokeCounts.xbox_refresh_library || 0)).toBe(1);
  await pad(page, 15);
  expect(await page.evaluate(() => (window as any).__invokeCounts.xbox_disconnect || 0)).toBe(0);

  await pad(page, 7);
  await frames(page, 20);
  for (let index = 0; index < 20 && !(await focusedRow.textContent())?.includes("Spotify"); index++) {
    await pad(page, 13);
  }
  await expect(focusedRow).toContainText("Spotify");
  await pad(page, 15);
  expect(await page.evaluate(() => (window as any).__invokeCounts.spotify_disconnect || 0)).toBe(0);
});

async function frames(page: Page, count: number) {
  await page.evaluate(async (count) => {
    for (let index = 0; index < count; index++) await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  }, count);
}

async function pad(page: Page, button: number) {
  await page.evaluate((index) => (window as any).__setGamepadButton(index, true), button);
  await frames(page, 3);
  await page.evaluate((index) => (window as any).__setGamepadButton(index, false), button);
  await frames(page, 3);
}

test("onboarding exposes every footer to a pad and keeps it visible at 1280x800", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/?fresh=true&surface=glass");
  const panel = page.locator("[data-onboarding-step]");
  await expect(panel).toHaveAttribute("data-onboarding-step", "welcome");
  await frames(page, 16);
  for (const step of ["theme", "accent", "surface"]) {
    await pad(page, 0);
    await expect(panel).toHaveAttribute("data-onboarding-step", step);
  }
  await pad(page, 0);
  await expect(panel).toHaveAttribute("data-onboarding-step", "home");
  await pad(page, 5);
  await expect(panel).toHaveAttribute("data-onboarding-step", "visual");
  await pad(page, 5);
  await expect(panel).toHaveAttribute("data-onboarding-step", "sources");
  await expect(panel.getByText("Scan Store Apps", { exact: true })).toBeVisible();
  for (const [step, rows, next] of [["sources", 7, "accounts"], ["accounts", 3, "essentials"], ["essentials", 4, "done"]] as const) {
    await expect(panel).toHaveAttribute("data-onboarding-step", step);
    if (step === "essentials") await expect(panel.getByText("Hide CreteOS when a game launches")).toBeVisible();
    for (let index = 0; index < rows; index++) await pad(page, 13);
    await expect(panel.locator('[data-onboarding-action="next"]')).toHaveAttribute("data-focused", "true");
    await pad(page, 12);
    await expect(panel.locator('[data-onboarding-action="next"]')).toHaveAttribute("data-focused", "false");
    await pad(page, 13);
    await pad(page, 14);
    await expect(panel.locator('[data-onboarding-action="back"]')).toHaveAttribute("data-focused", "true");
    await pad(page, 15);
    const bounds = await panel.boundingBox();
    const button = await panel.locator('[data-onboarding-action="next"]').boundingBox();
    expect(button!.y + button!.height).toBeLessThan(bounds!.y + bounds!.height);
    await pad(page, 0);
    await expect(panel).toHaveAttribute("data-onboarding-step", next);
  }
  await expect(panel.locator('[data-onboarding-action="next"]')).toHaveText("Done");
  await pad(page, 14);
  await pad(page, 0);
  await expect(panel).toHaveAttribute("data-onboarding-step", "essentials");
  await panel.getByRole("button", { name: "Next", exact: true }).click();
  await pad(page, 0);
  await expect(panel).toHaveCount(0);
});

async function reachOnboardingAccounts(page: Page) {
  const panel = page.locator("[data-onboarding-step]");
  await expect(panel).toHaveAttribute("data-onboarding-step", "welcome");
  await frames(page, 16);
  for (const step of ["theme", "accent", "surface", "home"]) {
    await pad(page, 0);
    await expect(panel).toHaveAttribute("data-onboarding-step", step);
  }
  await pad(page, 5);
  await expect(panel).toHaveAttribute("data-onboarding-step", "visual");
  await pad(page, 5);
  await expect(panel).toHaveAttribute("data-onboarding-step", "sources");
  await pad(page, 5);
  await expect(panel).toHaveAttribute("data-onboarding-step", "accounts");
  return panel;
}

test("onboarding Steam and Microsoft dialogs stack above setup and return to accounts", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/?fresh=true&surface=glass");
  const panel = await reachOnboardingAccounts(page);
  const overlay = page.locator("[data-onboarding-overlay]");

  await pad(page, 0);
  const steam = page.locator('[data-modal="steam-qr"]');
  await expect(steam).toBeVisible();
  const steamZ = await steam.locator("..").evaluate((el) => Number(getComputedStyle(el).zIndex));
  const overlayZ = await overlay.evaluate((el) => Number(getComputedStyle(el).zIndex));
  expect(steamZ).toBeGreaterThan(overlayZ);
  await pad(page, 1);
  await frames(page, 20);
  await expect(steam).toHaveCount(0);
  await expect(panel).toHaveAttribute("data-onboarding-step", "accounts");

  await pad(page, 13);
  await pad(page, 0);
  const microsoftTitle = page.locator('[data-modal=""]:not([data-onboarding-step])').getByText("Microsoft account", { exact: true });
  await expect(microsoftTitle).toBeVisible();
  const microsoftZ = await microsoftTitle.evaluate((el) => {
    let node: HTMLElement | null = el as HTMLElement;
    while (node && getComputedStyle(node).position !== "fixed") node = node.parentElement;
    return node ? Number(getComputedStyle(node).zIndex) : 0;
  });
  expect(microsoftZ).toBeGreaterThan(await overlay.evaluate((el) => Number(getComputedStyle(el).zIndex)));
  // The inherited modal suppresses input immediately after opening to avoid carrying A/B through.
  await frames(page, 25);
  await pad(page, 1);
  await frames(page, 20);
  await expect(microsoftTitle).toHaveCount(0);
  await expect(panel).toHaveAttribute("data-onboarding-step", "accounts");
});

test("CreteOS empty Home and three controller tabs", async ({ page }, info) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  const errors: Error[] = [];
  page.on("pageerror", error => errors.push(error));
  await page.goto("/?crete&catalog");
  await expect(page.locator(".crete-home")).toContainText("No games played yet");
  await expect(page.getByLabel("CreteOS", { exact: true })).toBeVisible();
  await frames(page, 40);
  await pad(page, 5);
  await expect(page.getByRole("button", { name: "Library", exact: true })).toHaveAttribute("aria-current", "page");
  await pad(page, 5);
  await expect(page.getByRole("button", { name: "Settings", exact: true })).toHaveAttribute("aria-current", "page");
  await pad(page, 5);
  await expect(page.getByRole("button", { name: "Home", exact: true })).toHaveAttribute("aria-current", "page");
  await page.screenshot({ path: info.outputPath("creteos-empty.png") });
  expect(errors).toEqual([]);
});

async function richCrete(page: Page, played = false, motion = false) {
  await page.route("https://fixture.test/**", route => route.fulfill({ contentType: "image/svg+xml", body:
    `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="800"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="#34414f"/><stop offset="1" stop-color="#122029"/></linearGradient></defs><rect width="600" height="800" fill="url(#g)"/><circle cx="300" cy="300" r="170" fill="#e9a93c" opacity=".12"/><text x="300" y="500" text-anchor="middle" fill="#f2eadb" font-size="40" font-family="sans-serif">TEST ARTWORK</text></svg>` }));
  await page.goto(`/?crete&rich${played ? "&played" : ""}${motion ? "&motion" : ""}`);
  await expect(page.getByRole("button", { name: "Home", exact: true })).toBeVisible();
  await frames(page, 40);
}

for (const viewport of [{ width: 1280, height: 800 }, { width: 1280, height: 720 }, { width: 800, height: 600 }, { width: 1920, height: 1080 }]) {
  test(`CreteOS Library layout and details at ${viewport.width}x${viewport.height}`, async ({ page }, info) => {
    await page.setViewportSize(viewport);
    const errors: Error[] = [];
    page.on("pageerror", error => errors.push(error));
    await richCrete(page);
    await page.getByRole("button", { name: "Library", exact: true }).click();
    const grid = page.locator('[data-tab-pane="games"] .crete-library-grid');
    await expect(grid.locator(".crete-game-card")).toHaveCount(24);
    const gridWidth = await grid.evaluate(el => el.scrollWidth <= el.clientWidth + 1);
    expect(gridWidth).toBe(true);
    const first = grid.locator(".crete-game-card").first();
    await expect(first.locator("img")).toBeVisible();
    await page.screenshot({ path: info.outputPath("creteos-library.png") });
    await page.keyboard.press("ArrowRight");
    await expect(grid.locator('[data-selected="true"]')).toHaveAttribute("aria-label", "Hollow Knight");
    await page.keyboard.press("Enter");
    const details = page.locator('[data-crete-details="true"]');
    await expect(details).toBeVisible();
    await expect(details.locator(".crete-details-info")).toContainText("Test Developer");
    await expect(details.locator('[data-details-scroll]')).toContainText("Test catalogue description");
    const panel = await details.locator(".crete-details-info").boundingBox();
    expect(panel!.x + panel!.width).toBeLessThanOrEqual(viewport.width);
    await page.screenshot({ path: info.outputPath("creteos-details.png") });
    await details.getByRole("button", { name: "Launch options", exact: true }).click();
    const menu = details.getByRole("menu", { name: "Launch options" });
    await expect(menu).toBeVisible();
    const menuBounds = await menu.boundingBox();
    expect(menuBounds!.x).toBeGreaterThanOrEqual(0);
    expect(menuBounds!.x + menuBounds!.width).toBeLessThanOrEqual(viewport.width);
    const label = await menu.getByRole("menuitem", { name: "Moonlight", exact: true }).boundingBox();
    expect(label!.x).toBeGreaterThanOrEqual(0);
    await page.screenshot({ path: info.outputPath("creteos-launch-menu.png") });
    await page.keyboard.press("Escape");
    await expect(menu).toHaveCount(0);
    await page.keyboard.press("Escape");
    await expect(details).toHaveCount(0);
    await expect(grid.locator('[data-selected="true"]')).toHaveAttribute("aria-label", "Hollow Knight");
    await page.keyboard.press("ArrowRight");
    await expect(grid.locator('[data-selected="true"]')).toHaveAttribute("aria-label", "Test Game 03");
    expect(errors).toEqual([]);
  });
}

test("CreteOS Library filters, inline search, source picker and sort", async ({ page }) => {
  await richCrete(page);
  await page.getByRole("button", { name: "Library", exact: true }).click();
  const library = page.locator('[data-tab-pane="games"]');
  const cards = library.locator(".crete-game-card");
  await library.getByRole("button", { name: "Local", exact: true }).click();
  await expect(cards).toHaveCount(16);
  await library.getByRole("button", { name: "Cloud", exact: true }).click();
  await expect(cards).toHaveCount(1);
  await library.getByRole("button", { name: "Owned", exact: true }).click();
  await expect(cards).toHaveCount(23);
  await library.getByRole("button", { name: "All Games", exact: true }).click();
  await library.getByRole("button", { name: "Search games", exact: true }).click();
  await library.getByRole("textbox", { name: "Search games" }).fill("hollow knight");
  await expect(cards).toHaveCount(1);
  await library.getByRole("textbox", { name: "Search games" }).press("Escape");
  await expect(cards).toHaveCount(24);
  await library.getByRole("button", { name: "Sources", exact: true }).click();
  await expect(library.getByRole("group", { name: "Library sources" })).toBeVisible();
  await pad(page, 1);
  await expect(library.getByRole("group", { name: "Library sources" })).toHaveCount(0);
  await library.getByRole("button", { name: "Sort games" }).click();
  await library.getByRole("button", { name: "A–Z", exact: true }).click();
  await expect(library.getByRole("group", { name: "Sort order" })).toHaveCount(0);
});

test("CreteOS played Home keeps hero separate from the rail", async ({ page }, info) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await richCrete(page, true);
  await expect(page.locator(".crete-hero h1")).toHaveText("Portal 2");
  await expect(page.locator(".crete-hero-cover")).toHaveCount(0);
  await expect(page.locator(".crete-hero-info")).toContainText("1h 33m");
  await expect(page.locator(".crete-rail .crete-game-card")).toHaveCount(23);
  await expect(page.locator('.crete-rail [aria-label="Portal 2"]')).toHaveCount(0);
  await page.screenshot({ path: info.outputPath("creteos-home.png") });
  await pad(page, 13);
  await expect(page.locator('.crete-rail [data-selected="true"]')).toHaveAttribute("aria-label", "Hollow Knight");
  await pad(page, 0);
  await expect(page.locator('[data-crete-details="true"]')).toBeVisible();
  await frames(page, 25);
  await pad(page, 1);
  await expect(page.locator('[data-crete-details="true"]')).toHaveCount(0);
  await expect(page.locator('.crete-rail [data-selected="true"]')).toHaveAttribute("aria-label", "Hollow Knight");
});

test("CreteOS Home news opens the article from the controller and returns to Library", async ({ page }) => {
  await page.goto("/?crete&rich&news");
  await expect(page.locator(".crete-news-card")).toContainText("Test gaming headline");
  await frames(page, 25);
  await pad(page, 13);
  await expect(page.locator('.crete-news-card[data-selected="true"]')).toBeVisible();
  await pad(page, 0);
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts["plugin:opener|open_url"])).toBe(1);
  await pad(page, 12);
  await page.getByRole("button", { name: "Home", exact: true }).click();
  await page.getByRole("button", { name: "Open Library", exact: true }).click();
  await expect(page.locator('[data-tab-pane="games"] .crete-game-card')).toHaveCount(24);
});

test("CreteOS details keeps native management and sends one launch command", async ({ page }) => {
  await richCrete(page, true);
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.locator('[data-tab-pane="games"] .crete-game-card').first().click();
  const details = page.locator('[data-crete-details="true"]');
  await details.getByRole("button", { name: "Launch options", exact: true }).click();
  await details.getByRole("menuitem", { name: "Manage game", exact: true }).click();
  await expect(details.getByRole("button", { name: "Launch options", exact: true })).toHaveAttribute("aria-expanded", "false");
  await expect(details.getByRole("button", { name: /Pin/ }).first()).toBeVisible();
  await details.getByRole("button", { name: "Launch options", exact: true }).click();
  await details.getByRole("menuitem", { name: "Game information", exact: true }).click();
  await expect(details.getByRole("button", { name: /Pin/ }).first()).not.toBeVisible();
  await details.locator("[data-launch-btn]").click();
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.launch_app)).toBe(1);
});

test("CreteOS Applications keeps native collections and returns to games", async ({ page }) => {
  await page.goto("/?crete&applications");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  const games = page.locator('[data-tab-pane="games"]');
  await games.getByRole("button", { name: "Sources", exact: true }).click();
  await games.getByRole("button", { name: "Applications", exact: true }).click();
  const apps = page.locator('[data-tab-pane="apps"]');
  await expect(apps.locator(".crete-game-card")).toHaveCount(2);
  await apps.getByRole("button", { name: "Sources", exact: true }).click();
  await apps.getByRole("button", { name: "Tools", exact: true }).click();
  await expect(apps.locator(".crete-game-card")).toHaveCount(1);
  await expect(apps.locator(".crete-game-card")).toHaveAttribute("aria-label", "Terminal");
  await apps.getByRole("button", { name: "Tools", exact: true }).click();
  await apps.getByRole("button", { name: "Game Library", exact: true }).click();
  await expect(games).toBeVisible();
  await expect(games.locator(".crete-library-toolbar")).toBeVisible();
});

for (const catalog of [false, true]) {
  test(`fresh Home backdrop with ${catalog ? "detected games" : "no games"}`, async ({ page }, info) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto(`/?fresh=true${catalog ? "&catalog" : ""}`);
    await expect(page.locator("[data-onboarding-step]")).toBeVisible();
    const home = page.locator("[data-home-root]");
    if (catalog) await expect(home).toContainText("Portal 2");
    else await expect(home).toContainText("Launch a game to see it here");
    await page.screenshot({ path: info.outputPath("fresh-home.png") });
  });
}

test("Data joystick scrolling preserves storage rows and moves monotonically", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Home", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Open helper tray", exact: true }).click();
  await page.locator('[data-modal="helper"]').getByRole("button", { name: "Settings", exact: true }).click();
  await page.getByText("Storage & troubleshooting", { exact: true }).click();
  const storage = page.getByText("Device Storage", { exact: true }).locator("..");
  await expect(storage).toContainText("D: — Games");
  await storage.evaluate((element) => { (window as any).__storageRow = element; });
  const callsBefore = await page.evaluate(() => (window as any).__invokeCounts.get_storage_info);
  await frames(page, 16);
  let previous = 0;
  await page.evaluate(() => (window as any).__setGamepadAxis(1, 1));
  for (let sample = 0; sample < 12; sample++) {
    await page.waitForTimeout(100);
    const current = await storage.evaluate((element) => {
      let scroller = element.parentElement;
      while (scroller && getComputedStyle(scroller).overflowY !== "auto") scroller = scroller.parentElement;
      return scroller?.scrollTop || 0;
    });
    expect(current).toBeGreaterThanOrEqual(previous - 1);
    previous = current;
  }
  await page.evaluate(() => (window as any).__setGamepadAxis(1, 0));
  expect(previous).toBeGreaterThan(0);
  await frames(page, 3);
  await page.evaluate(() => (window as any).__setGamepadAxis(1, -1));
  await page.waitForTimeout(900);
  await page.evaluate(() => (window as any).__setGamepadAxis(1, 0));
  const firstAction = page.locator("[data-settings-row].focused");
  await expect(firstAction).toContainText("Clear local launch history");
  const bounds = await firstAction.boundingBox();
  expect(bounds!.y).toBeGreaterThanOrEqual(124);
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(800);
  expect(await storage.evaluate((element) => element === (window as any).__storageRow)).toBe(true);
  expect(await page.evaluate(() => (window as any).__invokeCounts.get_storage_info)).toBe(callsBefore);
});

for (const [theme, surface] of [["crete", "glass"], ["space", "glass"], ["sky", "aero"], ["wash", "material"], ["space", "clear"], ["lofi", "obsidian"], ["cyberpunk", "neon"], ["webcore", "win9x"], ["onyx", "clear"]]) {
  test(`helper and refresh inherit ${theme}/${surface} surfaces`, async ({ page }, info) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto(`/?theme=${theme}&surface=${surface}&holdRefresh`);
    await expect(page.getByRole("button", { name: "Home", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Open helper tray", exact: true }).click();
    const tray = page.locator('[data-modal="helper"]');
    await expect(tray).toBeVisible();
    const getSurface = (element: Element) => {
      const style = getComputedStyle(element);
      return { background: style.background, border: style.border, shadow: style.boxShadow, blur: style.backdropFilter, radius: style.borderRadius };
    };
    const helperStyle = await tray.evaluate(getSurface);
    if (surface === "neon") expect(helperStyle.blur).toContain("blur(12px)");
    else expect(helperStyle.blur).toBe("none");
    if (["glass", "aero", "clear", "obsidian"].includes(surface)) {
      await expect(page.locator('[data-helper-tray-frost]')).toHaveCSS("backdrop-filter", /blur\(/);
    }
    if (theme === "cyberpunk" || surface === "win9x") expect(helperStyle.radius).toBe("0px");
    await expect(tray.locator(".lo-tray-row").last()).toHaveCSS("opacity", "1");
    await page.screenshot({ path: info.outputPath("helper.png") });
    await tray.getByRole("button", { name: "Controls", exact: true }).click();
    const controls = page.locator('[data-modal=""]');
    await expect(controls).toBeVisible();
    const standardStyle = await controls.evaluate(getSurface);
    if (["neon", "glass", "aero", "clear", "obsidian"].includes(surface)) {
      expect(helperStyle.background).not.toBe(standardStyle.background);
      if (surface === "neon") expect(helperStyle.blur).toContain("blur(12px)");
    } else {
      expect(standardStyle).toEqual(helperStyle);
    }
    await page.keyboard.press("Escape");
    await expect(controls).toHaveCount(0);
    await page.getByRole("button", { name: "Open helper tray", exact: true }).click();
    await tray.getByRole("button", { name: /Refresh/ }).click();
    const refresh = page.locator('[data-modal="library-refresh"]');
    await expect(refresh).toBeVisible();
    expect((await refresh.locator(".lo-loading-spinner").boundingBox())!.width).toBeGreaterThan(0);
    expect(await refresh.evaluate(getSurface)).toEqual(["neon", "glass", "aero", "clear", "obsidian"].includes(surface) ? standardStyle : helperStyle);
    expect(await refresh.locator("..").evaluate((element) => getComputedStyle(element).backdropFilter)).toBe("none");
    await page.screenshot({ path: info.outputPath("refresh.png") });
  });
}

for (const mode of ["full", "hidden"] as const) {
  test(`opens the helper tray on a single MENU press in ${mode} mode`, async ({ page }) => {
    await page.goto(`/?barMode=${mode}`);
    await expect(page.getByRole("button", { name: "Home", exact: true })).toBeVisible({ timeout: 10_000 });

    await page.evaluate(() => (window as any).__setGamepadButton(9, true));
    await expect(page.getByText("Helper", { exact: true })).toBeVisible();
    await page.evaluate(() => (window as any).__setGamepadButton(9, false));
  });
}

for (const input of ["keyboard", "controller"]) {
  test(`Home keeps the complete hero visible after returning from news with ${input}`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 600 });
    await page.route("https://fixture.test/**", route => route.fulfill({ contentType: "image/svg+xml", body: '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="900"><rect width="100%" height="100%" fill="#345"/></svg>' }));
    await page.goto("/?crete&rich&played&news");
    await expect(page.locator(".crete-hero h1")).toHaveText("Portal 2");
    await frames(page, 25);
    const move = async (down: boolean) => input === "controller" ? pad(page, down ? 13 : 12) : page.keyboard.press(down ? "ArrowDown" : "ArrowUp");
    await move(true); await move(true);
    await expect(page.locator('.crete-news-card[data-selected="true"]')).toBeVisible();
    await move(false); await move(false);
    await expect(page.locator('.crete-hero[data-focused="true"]')).toBeVisible();
    await expect.poll(async () => {
      const bounds = await page.locator(".crete-hero").boundingBox();
      return !!bounds && bounds.y >= 55 && bounds.y + bounds.height <= 544;
    }).toBe(true);
  });
}

test("GFN has one green Play that applies Auto bitrate and keeps artwork until handoff", async ({ page }) => {
  await richCrete(page);
  await page.goto("/?crete&rich&gfn&holdStreamWindow");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  await page.locator('[data-crete-details="true"]').getByRole("button", { name: "Play", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "GeForce NOW stream settings" });
  await expect(dialog.getByRole("button")).toHaveCount(2); // Play and Cancel.
  await dialog.getByLabel("Bitrate").selectOption("0");
  await expect(dialog.getByLabel("Bitrate")).toHaveValue("0");
  const play = dialog.getByRole("button", { name: "Play", exact: true });
  await expect(play).toHaveCSS("background-color", "rgb(124, 171, 0)");
  await play.click();
  const cover = page.locator("[data-stream-launch-cover]");
  await expect(cover).toBeVisible();
  await expect(cover.locator("h1,h2,button")).toHaveCount(0);
  await expect(cover.locator("[data-launch-message]")).toHaveText(/\S/);
  await expect(cover.locator(".crete-launch-message")).toHaveCSS("animation-name", "creteLaunchMessage");
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.start_geforce_game)).toBe(1);
  expect(await page.evaluate(() => (window as any).__lastGeforce.profile)).toMatchObject({ resolution: "1920x1080", bitrate: 0 });
  expect(await page.evaluate(() => (window as any).__invokeCounts.launch_geforce_game ?? 0)).toBe(0);
  await page.evaluate(() => (window as any).__releaseStreamWindow());
  await expect(cover).toHaveCount(0, { timeout: 12_000 });
});

test("Uninstalled mapped games offer green GFN Play plus native install and streaming alternatives", async ({ page }) => {
  await richCrete(page);
  await page.goto("/?crete&rich&gfn");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.getByRole("button", { name: "Play", exact: true })).toBeVisible();
  await expect(details.locator("[data-launch-btn]")).toHaveAttribute("data-cloud", "true");
  await expect(details.locator("[data-launch-btn]")).toHaveAttribute("data-provider", "geforceNow");
  await expect(details.locator("[data-launch-btn]")).toHaveCSS("background-color", "rgb(124, 171, 0)");
  await expect(details.getByRole("button", { name: "Launch options", exact: true })).toHaveCSS("background-color", "rgb(124, 171, 0)");
  await details.getByRole("button", { name: "Play", exact: true }).click();
  const gfn = details.getByRole("dialog", { name: "GeForce NOW stream settings" });
  await expect(gfn).toBeVisible();
  await gfn.getByLabel("Resolution", { exact: true }).selectOption("1920x1080");
  await gfn.getByLabel("Frame rate", { exact: true }).selectOption("120");
  await gfn.getByLabel("Bitrate").selectOption("20000");
  await expect(gfn.getByText(/Play uses your settings above/)).toBeVisible();
  await gfn.getByRole("button", { name: "Play", exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.start_geforce_game)).toBe(1);
  expect(await page.evaluate(() => (window as any).__lastGeforce.profile)).toMatchObject({ resolution: "1920x1080", fps: 120, bitrate: 20000 });
  expect(await page.evaluate(() => (window as any).__invokeCounts["plugin:opener|open_url"] ?? 0)).toBe(0);
  expect(await page.evaluate(() => (window as any).__invokeCounts.launch_app ?? 0)).toBe(0);
  await details.getByRole("button", { name: "Launch options", exact: true }).click();
  await expect(details.getByRole("menuitem", { name: "Install locally", exact: true })).toBeVisible();
  await details.getByRole("menuitem", { name: "Moonlight", exact: true }).click();
  const stream = details.getByRole("dialog", { name: "Moonlight stream settings" });
  await expect(stream).toBeVisible();
  await expect(stream.getByLabel("Host app")).toHaveValue("MoonDeckStream");
  await stream.getByRole("button", { name: "Cancel", exact: true }).click();
});

test("Home direct Play uses the mapped cloud page for an uninstalled hero", async ({ page }) => {
  await page.goto("/?crete&rich&played&gfnHero&direct");
  const primary = page.locator(".crete-hero .crete-play");
  await expect(primary).toHaveText("Play");
  await expect(primary).toHaveAttribute("data-cloud", "true");
  await primary.click();
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.launch_geforce_game)).toBe(1);
  expect(await page.evaluate(() => (window as any).__invokeCounts.launch_app ?? 0)).toBe(0);
});


test("Live host game takes priority over GFN and preserves display-specific stream options", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&hostGame");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await details.getByRole("button", { name: "Play", exact: true }).click();
  const stream = details.getByRole("dialog", { name: "Moonlight stream settings" });
  await expect(stream.getByLabel("Host app")).toHaveValue("Control Ultimate Edition");
  await stream.getByLabel("Display preset").selectOption("xreal");
  await expect(stream.getByLabel("Resolution")).toHaveValue("1920x1200");
  await expect(stream.getByLabel("Frame rate")).toHaveValue("90");
  await expect(stream.getByLabel("Bitrate")).toHaveValue("80000");
  await stream.getByLabel("Codec").selectOption("AV1");
  await stream.getByRole("button", { name: "Start stream", exact: true }).click();
  await expect(stream).not.toBeVisible({ timeout: 12_000 });
  expect(await page.evaluate(() => (window as any).__lastStream)).toMatchObject({ appName: "Control Ultimate Edition", steamId: null, profile: { resolution: "1920x1200", fps: 90, bitrate: 80000, codec: "AV1" } });
  expect(await page.evaluate(() => (window as any).__invokeCounts.launch_app ?? 0)).toBe(0);
});

test("Offline host falls back to GFN and Escape closes compact menu without closing details", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&hostOffline");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.getByRole("button", { name: "Play", exact: true })).toBeVisible();
  await page.keyboard.press("ArrowRight");
  await expect(details.getByRole("menu", { name: "Launch options" })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(details.getByRole("menu", { name: "Launch options" })).not.toBeVisible();
  await expect(details).toBeVisible();
});

test("MoonDeckStream passes the selected Steam ID and requires its own pairing", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&hostInstalled");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await details.getByRole("button", { name: "Launch options", exact: true }).click();
  await details.getByRole("menuitem", { name: "Moonlight", exact: true }).click();
  const stream = details.getByRole("dialog", { name: "Moonlight stream settings" });
  await expect(stream.getByLabel("Host app")).toHaveValue("MoonDeckStream");
  await stream.getByRole("button", { name: "Start stream", exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).__lastStream?.steamId)).toBe("870780");
  await page.evaluate(() => localStorage.removeItem("crete.host-snapshots.v1"));
  await page.goto("/?crete&rich&gfn&hostInstalled&buddyUnpaired");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  await expect(details.locator("[data-launch-btn]")).toHaveText("Play");
  await expect(details.locator("[data-launch-btn]")).toHaveAttribute("data-provider", "moonlight");
  await expect(details.locator("[data-launch-btn]")).toHaveCSS("background-color", "rgb(105, 117, 128)");
  await expect(details.getByRole("button", { name: "Launch options", exact: true })).toHaveCSS("background-color", "rgb(105, 117, 128)");
  await details.locator("[data-launch-btn]").click();
  await expect(stream.getByRole("button", { name: "Start stream", exact: true })).toBeDisabled();
  await expect(stream.getByRole("button", { name: "Pair MoonDeck Buddy", exact: true })).toBeVisible();
});
test("Installed mapped games keep local Play as the default", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&gfnLocal");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const primary = page.locator('[data-crete-details="true"] [data-launch-btn]');
  await expect(primary).toHaveText("Play");
  await expect(primary).toHaveAttribute("data-cloud", "false");
  await expect(primary).toHaveCSS("background-color", "rgb(201, 72, 42)");
  await expect(page.getByRole("button", { name: "Launch options", exact: true })).toHaveCSS("background-color", "rgb(201, 72, 42)");
  await primary.click();
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.launch_app)).toBe(1);
  expect(await page.evaluate(() => (window as any).__invokeCounts["plugin:opener|open_url"] ?? 0)).toBe(0);
});

test("Landscape covers fit completely and keep a readable title", async ({ page }) => {
  await page.route("https://fixture.test/**", route => route.fulfill({ contentType: "image/svg+xml", body: '<svg xmlns="http://www.w3.org/2000/svg" width="460" height="215"><rect width="460" height="215" fill="#334455"/><text x="20" y="120" fill="white">TEST WIDE ARTWORK</text></svg>' }));
  await page.goto("/?crete&rich&played");
  const card = page.locator(".crete-rail-cards .crete-game-card").first();
  await expect(card.locator("img")).toHaveAttribute("data-landscape", "true");
  await expect(card.locator(".crete-landscape-title")).toHaveText("Hollow Knight");
  expect(await card.locator("img").evaluate(el => getComputedStyle(el).objectFit)).toBe("contain");
});

test("Controller opens compact launch menu, chooses Moonlight and cancels only the stream panel", async ({ page }) => {
  await page.goto("/?crete&rich&gfn");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  await frames(page, 40);
  const details = page.locator('[data-crete-details="true"]');
  await pad(page, 15);
  await expect(details.getByRole("menu", { name: "Launch options" })).toBeVisible();
  await pad(page, 13);
  await expect(details.getByRole("menuitem", { name: "Moonlight", exact: true })).toBeFocused();
  await pad(page, 0);
  const stream = details.getByRole("dialog", { name: "Moonlight stream settings" });
  await expect(stream).toBeVisible();
  await pad(page, 1);
  await expect(stream).not.toBeVisible();
  await expect(details).toBeVisible();
});

test("Home selection scroll interpolates and a rapid reversal returns to the hero", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 600 });
  await richCrete(page, true, true);
  // Control animation time before input; sampling after input can miss every
  // intermediate frame on a busy native CI runner.
  await page.clock.install({ time: new Date("2026-10-13T12:00:00Z") });
  await page.clock.pauseAt(new Date("2026-10-13T12:00:01Z"));
  await page.keyboard.press("ArrowDown");
  await page.clock.runFor(80);
  const intermediate = await page.locator(".crete-home").evaluate(el => el.scrollTop);
  await page.clock.runFor(160);
  const destination = await page.locator(".crete-home").evaluate(el => el.scrollTop);
  expect(intermediate).toBeGreaterThan(0);
  expect(intermediate).toBeLessThan(destination);
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("ArrowUp");
  await page.keyboard.press("ArrowUp");
  await page.clock.runFor(240);
  await expect.poll(() => page.locator(".crete-home").evaluate(el => el.scrollTop)).toBe(0);
});


test("Steam host inventory makes MoonDeck the automatic default, while missing inventory never does", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&hostInstalled");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.locator("[data-launch-btn]")).toHaveText("Play");
  await details.locator("[data-launch-btn]").click();
  await expect(details.getByRole("dialog", { name: "Moonlight stream settings" }).getByLabel("Host app")).toHaveValue("MoonDeckStream");
  await page.goto("/?crete&rich&gfn&inventoryOffline");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  await expect(details.locator("[data-launch-btn]")).toHaveText("Play");
});


test("Crete library restores not-installed, pin, source triggers, right-click and visible management", async ({ page }) => {
  await page.goto("/?crete&rich");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  const library = page.locator('[data-tab-pane="games"]');
  await library.getByRole("button", { name: "Not installed", exact: true }).click();
  await expect(library.locator(".crete-game-card")).toHaveCount(7);
  await library.getByRole("button", { name: "All Games", exact: true }).click();
  const card = library.locator(".crete-game-card").first();
  await card.focus(); await pad(page, 2);
  await expect(card.getByLabel("Pinned", { exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.toggle_pin)).toBe(1);
  await pad(page, 6); await expect(library.locator(".crete-game-card")).toHaveCount(1);
  await pad(page, 7); await expect(library.locator(".crete-game-card")).toHaveCount(24);
  await card.click({ button: "right" });
  await expect(page.getByText("Unpin", { exact: true })).toBeVisible();
  await page.keyboard.press("Escape");
  await library.getByRole("button", { name: "Manage library", exact: true }).click();
  await expect(page.getByText("Add a game", { exact: true })).toBeVisible();
  await expect(page.getByText("Add Cloud Game", { exact: true })).toBeVisible();
  await page.keyboard.press("Escape"); await frames(page, 15); await pad(page, 8);
  await expect(page.getByText("Add a game", { exact: true })).toBeVisible();
});

test("Home restores pinned and collection rows, controller navigation and artwork modes", async ({ page }) => {
  await richCrete(page, true);
  await page.goto("/?crete&rich&played&parity&animated&homeMode=immersive");
  await expect(page.locator('[data-home-row="pinned"] .crete-game-card')).toHaveCount(1);
  await expect(page.locator('[data-home-row="collection:game:favourites"] .crete-game-card')).toHaveCount(2);
  await expect(page.locator(".crete-home")).toHaveAttribute("data-home-mode", "immersive");
  await expect(page.locator(".crete-hero video")).toHaveAttribute("src", "https://fixture.test/hero.mp4");
  await expect(page.locator(".crete-hero > img")).toHaveAttribute("src", "https://fixture.test/hero.svg");
  await page.keyboard.press("ArrowDown");
  await expect(page.locator('[data-home-row="pinned"] .crete-game-card')).toHaveAttribute("data-selected", "true");
  await page.keyboard.press("ArrowDown"); await page.keyboard.press("ArrowDown");
  await expect(page.locator('[data-home-row="collection:game:favourites"] .crete-game-card').first()).toHaveAttribute("data-selected", "true");
  await page.keyboard.press("Enter");
  await expect(page.locator('[data-crete-details="true"]')).toBeVisible();
});

test("Applications list mode keeps collections and native management", async ({ page }) => {
  await page.goto("/?crete&applications&listApps");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  const games = page.locator('[data-tab-pane="games"]');
  await games.getByRole("button", { name: "Sources", exact: true }).click();
  await games.getByRole("button", { name: "Applications", exact: true }).click();
  const apps = page.locator('[data-tab-pane="apps"]');
  await expect(apps.locator(".crete-app-list-item")).toHaveCount(2);
  expect(await apps.locator(".crete-library-grid").evaluate(el => getComputedStyle(el).gridTemplateColumns.split(" ").length)).toBe(2);
  await apps.getByRole("button", { name: "Sources", exact: true }).click();
  await apps.getByRole("button", { name: "Tools", exact: true }).click();
  await expect(apps.locator(".crete-app-list-item")).toHaveCount(1);
  await apps.getByRole("button", { name: "Manage library", exact: true }).click();
  await expect(page.getByText("Add an app", { exact: true })).toBeVisible();
});

test("Crete new controls use the selected French language and retain Spotify mini-player", async ({ page }) => {
  await page.goto("/?crete&rich&lang=fr");
  await page.getByRole("button", { name: "Bibliothèque", exact: true }).click();
  await expect(page.getByRole("button", { name: "Non installé", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Gérer la bibliothèque", exact: true })).toBeVisible();
  await expect(page.locator(".crete-spotify-mini")).toContainText("Test Track");
});

test("Crete details preserves actual download-size information", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&sizes");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const info = page.locator(".crete-details-info");
  await expect(info).toContainText("Download size");
  await expect(info).toContainText("2.0 GB");
});


test("Bosgame MoonDeck is preferred over a local copy, with explicit local Play retained", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&gfnLocal&hostInstalled");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.locator("[data-launch-btn]")).toHaveText("Play");
  await details.getByRole("button", { name: "Launch options", exact: true }).click();
  await details.getByRole("menuitem", { name: "Local", exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.launch_app)).toBe(1);
  expect(await page.evaluate(() => (window as any).__invokeCounts.launch_moonlight ?? 0)).toBe(0);
});

test("Daily host refresh never blocks local Play and keeps purple Play during manual refresh", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&gfnLocal&hostInstalled&holdHost");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.locator("[data-launch-btn]")).toHaveAttribute("data-provider", "local");
  await expect(details.locator(".crete-launch-loading")).toHaveCount(0);
  await expect(details.getByRole("button", { name: "Launch options", exact: true })).toBeVisible();
  await page.evaluate(() => (window as any).__releaseHostCheck());
  await expect(details.locator("[data-launch-btn]")).toHaveAttribute("data-provider", "moonlight");
  await details.locator("[data-launch-btn]").click();
  const stream = details.getByRole("dialog", { name: "Moonlight stream settings" });
  await stream.getByRole("button", { name: "Refresh host", exact: true }).click();
  await expect(stream.getByRole("button", { name: "Checking…", exact: true })).toBeDisabled();
  await expect(details.locator("[data-launch-btn]")).toHaveCSS("background-color", "rgb(105, 117, 128)");
  await expect(details.locator(".crete-launch-loading")).toHaveCount(0);
  await expect(stream.getByRole("button", { name: "Start stream", exact: true })).toBeEnabled();
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.get_host_installed_games)).toBe(2);
  await page.evaluate(() => (window as any).__releaseHostCheck());
  await expect(stream.getByRole("button", { name: "Refresh host", exact: true })).toBeEnabled();
});

async function savedHost(page: Page, age = 0) {
  await page.addInitScript(age => {
    if (!localStorage.getItem("crete.host-snapshots.v1")) localStorage.setItem("crete.host-snapshots.v1", JSON.stringify({ bosgame: {
      attemptedAt: Date.now() - age, checkedAt: Date.now() - age, apps: ["MoonDeckStream", "Desktop"],
      games: [{ steamAppId: "870780", name: "Control Ultimate Edition" }], buddy: { paired: true, version: 8 },
    } }));
  }, age);
}

test("Saved Moonlight availability survives reload without host requests", async ({ page }) => {
  await savedHost(page);
  await page.goto("/?crete&rich&played&gfnHero&hostOffline");
  const hero = page.locator(".crete-hero");
  await expect(hero.locator(".crete-play")).toHaveCSS("background-color", "rgb(105, 117, 128)");
  await page.reload();
  await expect(hero.locator(".crete-play")).toHaveCSS("background-color", "rgb(105, 117, 128)");
  for (const command of ["get_host_installed_games", "get_moonlight_apps", "get_moondeck_status"]) {
    expect(await page.evaluate(command => (window as any).__invokeCounts[command] ?? 0, command)).toBe(0);
  }
});

test("Expired snapshot remains usable while refresh is pending and after it fails", async ({ page }) => {
  await savedHost(page, 86400001);
  await page.goto("/?crete&rich&gfn&holdHost&hostOffline&inventoryOffline");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const primary = page.locator('[data-crete-details="true"] [data-launch-btn]');
  await expect(primary).toHaveAttribute("data-provider", "moonlight");
  await page.evaluate(() => (window as any).__releaseHostCheck());
  await expect(primary).toHaveAttribute("data-provider", "moonlight");
  await page.reload();
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  await expect(primary).toHaveAttribute("data-provider", "moonlight");
  expect(await page.evaluate(() => (window as any).__invokeCounts.get_host_installed_games ?? 0)).toBe(0);
});

test("Offline network selects installed local play without host or cloud requests", async ({ page, context }) => {
  await savedHost(page);
  await page.goto("/?crete&rich&gfn&gfnLocal");
  await context.setOffline(true);
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const primary = page.locator('[data-crete-details="true"] [data-launch-btn]');
  await expect(primary).toHaveAttribute("data-provider", "local");
  await primary.click();
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.launch_app)).toBe(1);
  expect(await page.evaluate(() => (window as any).__invokeCounts.get_host_installed_games ?? 0)).toBe(0);
  expect(await page.evaluate(() => (window as any).__invokeCounts["plugin:opener|open_url"] ?? 0)).toBe(0);
});

test("A failed cached Moonlight launch offers explicit local play", async ({ page }) => {
  await savedHost(page);
  await page.goto("/?crete&rich&gfn&gfnLocal&streamOffline");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.locator("[data-launch-btn]")).toHaveAttribute("data-provider", "moonlight");
  await details.locator("[data-launch-btn]").click();
  const stream = details.getByRole("dialog", { name: "Moonlight stream settings" });
  await stream.getByRole("button", { name: "Start stream", exact: true }).click();
  await expect(stream.getByRole("alert")).toContainText("Host unavailable");
  const error = details.locator(".crete-launch-error");
  await expect(error).toContainText("Host unavailable");
  const [play, arrow, message] = await Promise.all([
    details.locator("[data-launch-btn]").boundingBox(),
    details.getByRole("button", { name: "Launch options", exact: true }).boundingBox(),
    error.boundingBox(),
  ]);
  expect(Math.abs(arrow!.y - play!.y)).toBeLessThan(1);
  expect(Math.abs(arrow!.x - (play!.x + play!.width))).toBeLessThan(1);
  expect(message!.y).toBeGreaterThanOrEqual(play!.y + play!.height);
  await expect(error).toHaveCSS("font-style", "italic");
  await stream.getByRole("button", { name: "Play locally", exact: true }).click();
  await expect(stream).not.toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.launch_app)).toBe(1);
});


test("docked external controller operates details with an idle built-in controller", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&hostInstalled");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  await page.evaluate(() => {
    const external = navigator.getGamepads()[0]!;
    const builtIn = { mapping: "standard", index: 0, axes: [0, 0, 0, 0],
      buttons: Array.from({ length: 16 }, () => ({ pressed: false, value: 0, touched: false })) };
    Object.defineProperty(navigator, "getGamepads", { configurable: true, value: () => [builtIn, external] });
  });
  await frames(page, 35);
  await pad(page, 0);
  await expect(page.getByRole("dialog", { name: "Moonlight stream settings" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-controller-cursor", "hidden");
  await expect(page.locator("body")).toHaveCSS("cursor", "none");
  await page.mouse.move(12, 12);
  await expect(page.locator("html")).not.toHaveAttribute("data-controller-cursor", "hidden");
  await pad(page, 1);
  await expect(page.getByRole("dialog", { name: "Moonlight stream settings" })).not.toBeVisible();
});

test("Moonlight controller opens dropdown choices and reaches the bottom actions", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 600 });
  await page.goto("/?crete&rich&gfn&hostInstalled");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await details.getByRole("button", { name: "Play", exact: true }).click();
  const stream = details.getByRole("dialog", { name: "Moonlight stream settings" });
  await expect(stream).toBeVisible();
  await frames(page, 10);
  await stream.getByLabel("Codec").focus();
  await pad(page, 0);
  const choices = stream.getByRole("listbox", { name: "Codec" });
  await expect(choices).toBeVisible();
  await pad(page, 13); await pad(page, 0);
  await expect(stream.getByLabel("Codec", { exact: true })).toHaveValue("H.264");
  await pad(page, 13);
  await expect(stream.getByRole("button", { name: "Start stream", exact: true })).toBeFocused();
  await pad(page, 13);
  await expect(stream.getByRole("button", { name: "Refresh host", exact: true })).toBeFocused();
  await pad(page, 13);
  const open = stream.getByRole("button", { name: "Open Moonlight", exact: true });
  await expect(open).toBeFocused();
  const bounds = await open.boundingBox(); const dialog = await stream.boundingBox();
  expect(bounds!.y).toBeGreaterThanOrEqual(dialog!.y); expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(dialog!.y + dialog!.height);
  await pad(page, 13); await pad(page, 0);
  await expect(stream).not.toBeVisible();
});


test("Xbox activity-ID titles show metadata, account hours and preferred review videos", async ({ page }) => {
  await richCrete(page);
  await page.goto("/?crete&xboxDetails");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "A Plague Tale: Requiem", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.getByText("Asobo Studio", { exact: true })).toBeVisible();
  await expect(details.getByText("15h 22m", { exact: true })).toBeVisible();
  await expect(details.getByText("20 unlocked", { exact: true })).toBeVisible();
  await expect(details.getByText("330 / 1000", { exact: true })).toBeVisible();
  const media = details.locator('[data-details-media-index]');
  await expect(media.nth(0)).toHaveAttribute("aria-label", /Whitelight/);
  await expect(media.nth(1)).toHaveAttribute("aria-label", /Skill Up/);
  await expect.poll(() => page.evaluate(() => (window as any).__invokeCounts.fetch_title_store_metadata ?? 0)).toBe(1);
});


test("Streaming menu opens settings before profile hydration and B returns to Play", async ({ page }) => {
  await richCrete(page);
  await page.goto("/?crete&rich&gfn&holdGfnProfile");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await details.getByRole("button", { name: "Launch options", exact: true }).click();
  await details.getByRole("menuitem", { name: "GeForce NOW", exact: true }).click();
  const dialog = details.getByRole("dialog", { name: "GeForce NOW stream settings" });
  await expect(dialog).toBeVisible();
  await expect.poll(() => page.evaluate(() => typeof (window as any).__releaseGfnProfile)).toBe("function");
  await pad(page, 1);
  await expect(dialog).not.toBeVisible();
  await expect(details.getByRole("button", { name: "Play", exact: true })).toBeFocused();
  await page.evaluate(() => (window as any).__releaseGfnProfile());
  await expect(dialog).not.toBeVisible();
  await details.getByRole("button", { name: "Launch options", exact: true }).click();
  await details.getByRole("menuitem", { name: "Moonlight", exact: true }).click();
  await expect(details.getByRole("dialog", { name: "Moonlight stream settings" })).toBeVisible();
});


test("Game media retains four preferred reviews and four original trailers", async ({ page }) => {
  await richCrete(page);
  await page.goto("/?crete&rich&manyMedia");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Portal 2", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.locator('[data-media-type="youtube"]')).toHaveCount(4);
  await expect(details.locator('[data-media-type="trailer"]')).toHaveCount(4);
  await expect(details.locator('[data-media-type="youtube"]').nth(3)).toHaveAttribute("aria-label", /Noah Caldwell-Gervais/);
});


test("Luna titles show informational metadata without replacing their launch identity", async ({ page }) => {
  await richCrete(page);
  await page.goto("/?crete&rich&lunaDetails");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Dispatch", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.getByText("Luna Info Developer", { exact: true })).toBeVisible();
  await expect(details.getByText("Cloud game informational description", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => (window as any).__lastStoreMeta)).toEqual({ source: "steam", appId: "2592160" });
  await expect(details.getByText("Cloud", { exact: true }).first()).toBeVisible();
});


test("Home hero title and artwork open details by touch without launching", async ({ page }) => {
  await richCrete(page, true);
  await expect(page.locator(".crete-hero h1")).toHaveText("Portal 2");
  const title = await page.locator(".crete-hero h1").boundingBox();
  await page.mouse.click(title!.x + title!.width / 2, title!.y + title!.height / 2);
  await expect(page.locator('[data-crete-details="true"]')).toBeVisible();
  expect(await page.evaluate(() => (window as any).__invokeCounts.launch_app || 0)).toBe(0);
});

test("Pointer-opened provider menu shows D-pad focus and activates the selected provider", async ({ page }) => {
  await page.goto("/?crete&rich&gfn&hostInstalled");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await page.getByRole("button", { name: "Control Ultimate Edition", exact: true }).click();
  const details = page.locator('[data-crete-details="true"]');
  await details.getByRole("button", { name: "Launch options", exact: true }).click();
  await frames(page, 10);
  await pad(page, 13);
  const moonlight = details.getByRole("menuitem", { name: "Moonlight", exact: true });
  await expect(moonlight).toBeFocused();
  await expect(moonlight).toHaveCSS("outline-style", "solid");
  await expect(moonlight).toHaveCSS("outline-width", "2px");
  await pad(page, 0);
  await expect(details.getByRole("dialog", { name: "Moonlight stream settings" })).toBeVisible();
});


test("Luna hero and details share purple Play while Home and Library have two pixel focus", async ({ page }) => {
  await page.goto("/?crete&rich&lunaDetails&played");
  const hero = page.locator(".crete-hero");
  await expect(hero.locator(".crete-play")).toHaveCSS("background-color", "rgb(145, 70, 255)");
  await expect(hero).toHaveCSS("outline-width", "2px");
  await hero.locator(".crete-play").click();
  const details = page.locator('[data-crete-details="true"]');
  await expect(details.locator("[data-launch-btn]")).toHaveCSS("background-color", "rgb(145, 70, 255)");
  await expect(details.getByRole("button", { name: "Launch options", exact: true })).toHaveCSS("background-color", "rgb(145, 70, 255)");
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await expect(page.locator('.crete-game-card[data-selected="true"]').first()).toHaveCSS("outline-width", "2px");
});


test("Connected Microsoft account respects uninstalled visibility and settings open actual clients", async ({ page }) => {
 await page.goto("/?crete&rich&connectedAccounts");
 await page.getByRole("button",{name:"Settings",exact:true}).click();
 await page.getByText("Library & accounts",{exact:true}).click();
 await frames(page,30);
 await page.getByText("Show not-installed games",{exact:true}).click();
 await expect.poll(() => page.evaluate(() => (window as any).__savedSettings?.show_uninstalled_games)).toBe(false);
 await frames(page,30);
 expect(await page.evaluate(() => (window as any).__savedSettings.show_uninstalled_games)).toBe(false);
 await page.getByText("Startup & streaming",{exact:true}).click();
 await page.getByText("Open Moonlight",{exact:true}).click();
 await expect.poll(() => page.evaluate(() => (window as any).__lastStreamingClient)).toEqual({provider:"moonlight"});
 await page.getByText("Open GeForce NOW",{exact:true}).click();
 await expect.poll(() => page.evaluate(() => (window as any).__lastStreamingClient)).toEqual({provider:"geforceNow"});
 await page.getByText("About & updates",{exact:true}).click();
 await expect(page.getByText("CreteOS v0.1.0 · fixture",{exact:true})).toBeVisible();
 await page.getByText("Check for Updates",{exact:true}).click();
 await expect(page.getByText("No published Windows release; preview builds are separate",{exact:true})).toBeVisible();
});

test("Games article images render from the native scoped cache", async ({ page }) => {
 await page.route("http://asset.localhost/**", route => route.fulfill({contentType:"image/svg+xml",body:'<svg xmlns="http://www.w3.org/2000/svg" width="120" height="60"><rect width="120" height="60" fill="orange"/></svg>'}));
 await page.goto("/?crete&rich&played&newsAsset");
 const card = page.getByRole("button",{name:/Cached Games article/});
 const image = card.locator("img");
 await expect(image).toHaveAttribute("src",/asset.localhost.*media.*news/);
 await card.scrollIntoViewIfNeeded();
 await expect.poll(() => image.evaluate(node => (node as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
});
