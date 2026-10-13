interface Props {
  size?: number;
  color?: string;
}

/** Official Meander C; geometry and single colour follow reference/icon/README.md. */
export function CreteMark({ size = 26, color = "#E9A93C" }: Props) {
  return (
    <svg width={size} height={size} viewBox="0 0 112 100" fill="none" aria-hidden="true">
      <path d="M92 20 H20 V80 H92 V60 H40 V40 H72" stroke={color} strokeWidth="13" strokeLinecap="square" strokeLinejoin="miter" />
    </svg>
  );
}
