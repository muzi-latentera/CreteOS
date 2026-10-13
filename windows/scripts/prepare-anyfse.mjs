import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { win32 } from "node:path";
import { parseArgs } from "node:util";

const { values } = parseArgs({
  options: {
    launcher: { type: "string" },
    "gpd-tool": { type: "string" },
    "gpd-args": { type: "string", default: "" },
    "gpd-admin": { type: "boolean", default: false },
    config: { type: "string" },
    output: { type: "string", default: "AnyFSE.CreteOS.json" },
    help: { type: "boolean", default: false },
  },
});

if (values.help) {
  console.log('node scripts/prepare-anyfse.mjs --launcher "C:\\path\\creteos.exe" --gpd-tool "C:\\path\\MotionAssistant.exe" [--config existing.json] [--gpd-args "..."] [--gpd-admin] [--output profile.json]');
  process.exit(0);
}

function executable(value, name) {
  if (!value || !win32.isAbsolute(value) || win32.extname(value).toLowerCase() !== ".exe") {
    throw new Error(`${name} must be an absolute Windows .exe path.`);
  }
  return win32.normalize(value);
}

try {
  const launcher = executable(values.launcher, "--launcher");
  const tool = executable(values["gpd-tool"], "--gpd-tool");
  if (win32.basename(launcher).toLowerCase() !== "creteos.exe") {
    throw new Error("--launcher must point to the installed creteos.exe.");
  }
  // Generate a separate, reviewable profile; never modify a live configuration.
  if (existsSync(values.output)) throw new Error(`Output already exists: ${values.output}`);
  const config = values.config ? JSON.parse(readFileSync(values.config, "utf8").replace(/^\uFEFF/, "")) : {};
  if (!config || typeof config !== "object" || Array.isArray(config)) throw new Error("Invalid AnyFSE configuration object.");
  if (config.StartupApps !== undefined && !Array.isArray(config.StartupApps)) throw new Error("StartupApps must be an array.");
  config.Launcher = {
    ...config.Launcher,
    Path: launcher,
    CustomSettings: true,
    StartCommand: launcher,
    StartArg: "",
    ProcessName: "creteos.exe",
    ClassName: "",
    WindowTitle: "CreteOS",
    ProcessNameAlt: "",
    ClassNameAlt: "",
    WindowTitleAlt: "",
  };
  config.StartupApps = [
    ...(config.StartupApps ?? []).filter((app) => win32.normalize(app.Path ?? "").toLowerCase() !== tool.toLowerCase()),
    { Path: tool, Args: values["gpd-args"], Enabled: true, AsAdmin: values["gpd-admin"] },
  ];
  config.Splash = { ...config.Splash, CustomText: "CreteOS", ShowText: true, ShowLogo: false };
  writeFileSync(values.output, `${JSON.stringify(config, null, 2)}\n`, { flag: "wx" });
  console.log(`Prepared ${values.output}. Install AnyFSE, apply this profile as described in docs/WINDOWS_SETUP.md, and select AnyFSE in Windows Settings.`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
