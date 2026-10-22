import {
	contrastRatio,
	darken,
	hexToRgba,
	rgbaToHex,
} from "@/utils/colorUtils";

/** Tenant theme from org metadata (`org.metadata.theme`). */
export type OrgThemeColors = {
	navstyle?: string;
	primary?: string;
	primary_light?: string;
	primary_dark?: string;
	accent?: string;
	accent_light?: string;
	accent_dark?: string;
};

export type BrandTokens = {
	solid: string;
	solidHover: string;
	fg: string;
	subtle: string;
	emphasis: string;
};

const WHITE = "#FFFFFF";
const INK = "#0A0A0A";
const CONNECT_BLUE = "#11299E";
const MIN_TEXT_CONTRAST = 4.5;

/** Brand slot for platform hosts (partners/staff): the neutral black CTA with a Connect Blue accent. */
export const PLATFORM_BRAND: BrandTokens = {
	solid: INK,
	solidHover: "#1F1F1F",
	fg: WHITE,
	subtle: "#E8ECFA",
	emphasis: CONNECT_BLUE,
};

const isHex = (color?: string): boolean =>
	typeof color === "string" && hexToRgba(color) !== null;

// The foreground (white or ink) with the higher contrast against the color
const bestForeground = (color: string): string =>
	contrastRatio(WHITE, color) >= contrastRatio(INK, color) ? WHITE : INK;

const bestContrast = (color: string): number =>
	Math.max(contrastRatio(WHITE, color), contrastRatio(INK, color));

// Mix the color over white at the given opacity (0–1), returned as #RRGGBB
const tint = (color: string, opacity: number): string => {
	const { r, g, b } = hexToRgba(color);
	const mix = (c: number) => Math.round(255 - (255 - c) * opacity);
	return rgbaToHex({ r: mix(r), g: mix(g), b: mix(b) }, true).toUpperCase();
};

/**
 * Computes the tenant brand slot from its primary colors. The solid fill keeps the tenant's
 * primary when a readable foreground exists for it (4.5:1), otherwise it falls back to
 * primary_dark. See docs/frontend/UI_UX_PLAN.md §2.1.
 * @param {string} primary - Tenant primary color (#RRGGBB).
 * @param {string} primaryDark - Tenant dark primary color (#RRGGBB).
 * @returns {BrandTokens} The brand tokens; PLATFORM_BRAND when the colors are not valid hex.
 */
export const computeBrandTokens = (
	primary: string,
	primaryDark: string
): BrandTokens => {
	if (!isHex(primary)) return PLATFORM_BRAND;
	const dark = isHex(primaryDark) ? primaryDark : primary;

	const solid = bestContrast(primary) >= MIN_TEXT_CONTRAST ? primary : dark;
	const hoverSource = solid === primary && dark !== primary ? dark : solid;
	const solidHover =
		hoverSource === solid
			? rgbaToHex(darken(hexToRgba(solid), 8), true).toUpperCase()
			: hoverSource;
	const emphasis =
		contrastRatio(dark, WHITE) >= contrastRatio(primary, WHITE)
			? dark
			: primary;

	return {
		solid,
		solidHover,
		fg: bestForeground(solid),
		subtle: tint(solid, 0.1),
		emphasis,
	};
};

/**
 * Builds the Chakra theme for a tenant from the base theme and the org's metadata.theme.
 * The legacy keys (primary, accent, navbar, sidebar, status, logo) are produced exactly as
 * _app.tsx always did; `brand` is the new contrast-safe slot for the redesigned screens.
 * @param {object} org - Org details (may be null).
 * @param {object} base - The base Chakra theme (styles/themes.tsx `light`).
 * @param {object} [options] - Options.
 * @param {boolean} [options.platform] - True on platform hosts (no tenant): brand = black CTA.
 * @returns {object} The theme to pass to ChakraProvider.
 */
export const buildOrgTheme = (
	org: { metadata?: { theme?: OrgThemeColors } } | null | undefined,
	base: any,
	options: { platform?: boolean } = {}
): any => {
	const colors = org?.metadata?.theme;
	const theme = colors
		? {
				...base,
				colors: {
					...base.colors,
					navstyle: colors.navstyle,
					primary: {
						...base.colors.primary,
						light:
							colors?.primary_light || base.colors.primary.light,
						DEFAULT: colors?.primary || base.colors.primary.DEFAULT,
						dark: colors?.primary_dark || base.colors.primary.dark,
					},
					accent: {
						...base.colors.accent,
						light: colors?.accent_light || base.colors.accent.light,
						DEFAULT: colors?.accent || base.colors.accent.DEFAULT,
						dark: colors?.accent_dark || base.colors.accent.dark,
					},
				},
			}
		: {
				...base,
			};

	// NavBar style colors (light or default dark)
	const lightNav = colors?.navstyle === "light";
	theme.colors = {
		...theme.colors,
		navbar: {
			...base.colors.navbar,
			bg: lightNav ? theme.colors.primary.DEFAULT : "#FFF",
			bgAlt: lightNav ? "#FFFFFF30" : "#00000020",
			text: lightNav ? "#FFFFFFEE" : "#000000DD",
			textLight: lightNav ? "#FFFFFF70" : "#00000070",
			dark: lightNav ? "#FFFFFF" : "#000000",
		},
		sidebar: {
			...base.colors.sidebar,
			bg: lightNav ? "#FFF" : theme.colors.primary.DEFAULT,
			text: lightNav ? "#333" : "#FFF",
			dark: lightNav ? "#000000" : "#FFFFFF",
			sel: lightNav
				? theme.colors.primary.DEFAULT
				: theme.colors.primary.dark, // Selection color
			divider: lightNav
				? theme.colors.primary.light + "40"
				: theme.colors.primary.light,
		},
		status: {
			...base.colors.status,
			bg: lightNav ? theme.colors.primary.DEFAULT + "30" : "#00000050",
			bgLight: lightNav ? "#FFF" : "#00000030",
			text: lightNav ? "#111" : "#FFF",
			wm: lightNav ? "#00000050" : "#FFFFFF50", // Watermark color
			wmLight: lightNav ? "#00000030" : "#FFFFFF25",
			title: lightNav ? theme.colors.primary.dark : "#FFD93B",
			borderRightColor: lightNav ? "#FFF" : "#00000050",
		},
		logo: {
			text: lightNav ? "#fff" : "primary.dark",
		},
		brand: options.platform
			? PLATFORM_BRAND
			: computeBrandTokens(
					theme.colors.primary.DEFAULT,
					theme.colors.primary.dark
				),
	};

	return theme;
};
