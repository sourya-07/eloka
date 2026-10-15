const PALETTES = [
	{ text: "#1e40af", bg: "#dbeafe" }, // blue
	{ text: "#5b21b6", bg: "#ede9fe" }, // violet
	{ text: "#065f46", bg: "#d1fae5" }, // emerald
	{ text: "#92400e", bg: "#fef3c7" }, // amber
	{ text: "#9d174d", bg: "#fce7f3" }, // pink
	{ text: "#0e7490", bg: "#cffafe" }, // cyan
	{ text: "#166534", bg: "#dcfce7" }, // green
];

export function initials(name = "") {
	const words = name.trim().split(/\s+/).filter(Boolean);
	if (!words.length) return "?";
	return words.length === 1
		? words[0].slice(0, 2).toUpperCase()
		: (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

export function avatarColor(name = "") {
	const n = name.split("").reduce((s, c) => s + c.charCodeAt(0), 0);
	return PALETTES[n % PALETTES.length];
}

export function maskAccount(acc = "") {
	if (!acc || acc.length <= 4) return acc;
	return "••••" + acc.slice(-4);
}

export function formatInr(value) {
	const n = parseFloat(value);
	if (!n || isNaN(n)) return null;
	return n.toLocaleString("en-IN");
}
