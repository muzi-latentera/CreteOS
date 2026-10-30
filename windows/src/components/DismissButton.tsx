import type { ButtonHTMLAttributes } from "react";
import { useTheme } from "../contexts/ThemeContext";

/** Shared, theme-aware secondary action for toast/dialog dismiss controls. */
export default function DismissButton({ style, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  const { theme, isDark, surfaceStyle } = useTheme();
  return <button type="button" {...props} style={{
    fontFamily: "inherit", fontSize: 13, fontWeight: 600, lineHeight: 1.3,
    padding: "10px 16px", borderRadius: surfaceStyle === "win9x" ? 0 : 10,
    color: theme.text, background: isDark ? "rgba(255,255,255,.08)" : "rgba(0,0,0,.05)",
    border: `1px solid ${isDark ? "rgba(255,255,255,.16)" : "rgba(0,0,0,.14)"}`,
    cursor: "pointer", ...style,
  }} />;
}
