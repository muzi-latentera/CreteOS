// `a.localeCompare(b, undefined, options)` builds a new collator on every call.
// Sorting ~550 games that way took about 17 ms per controller press on the G1
// (14 Nov). One shared collator gives the same order for a fraction of the cost.
const baseCollator = new Intl.Collator(undefined, { sensitivity: "base" });

/** Case- and accent-insensitive comparison, identical to localeCompare with sensitivity "base". */
export const compareText = (a: string | undefined | null, b: string | undefined | null) => baseCollator.compare(a || "", b || "");
