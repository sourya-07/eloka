import { colorThemes } from "constants/colorThemes";
import {
	buildOrgTheme,
	computeBrandTokens,
	PLATFORM_BRAND,
} from "helpers/buildOrgTheme";
import { light } from "styles/themes";
import { contrastRatio } from "utils/colorUtils";

// The theme merge exactly as pages/_app.tsx did it before buildOrgTheme (reference for parity)
const legacyTheme = (org) => {
	const colors = org?.metadata?.theme;
	const theme = colors
		? {
				...light,
				colors: {
					...light.colors,
					navstyle: colors.navstyle,
					primary: {
						...light.colors.primary,
						light:
							colors?.primary_light || light.colors.primary.light,
						DEFAULT:
							colors?.primary || light.colors.primary.DEFAULT,
						dark: colors?.primary_dark || light.colors.primary.dark,
					},
					accent: {
						...light.colors.accent,
						light:
							colors?.accent_light || light.colors.accent.light,
						DEFAULT: colors?.accent || light.colors.accent.DEFAULT,
						dark: colors?.accent_dark || light.colors.accent.dark,
					},
				},
			}
		: { ...light };
	const lightNav = colors?.navstyle === "light";
	theme.colors = {
		...theme.colors,
		navbar: {
			...light.colors.navbar,
			bg: lightNav ? theme.colors.primary.DEFAULT : "#FFF",
			bgAlt: lightNav ? "#FFFFFF30" : "#00000020",
			text: lightNav ? "#FFFFFFEE" : "#000000DD",
			textLight: lightNav ? "#FFFFFF70" : "#00000070",
			dark: lightNav ? "#FFFFFF" : "#000000",
		},
		sidebar: {
			...light.colors.sidebar,
			bg: lightNav ? "#FFF" : theme.colors.primary.DEFAULT,
			text: lightNav ? "#333" : "#FFF",
			dark: lightNav ? "#000000" : "#FFFFFF",
			sel: lightNav
				? theme.colors.primary.DEFAULT
				: theme.colors.primary.dark,
			divider: lightNav
				? theme.colors.primary.light + "40"
				: theme.colors.primary.light,
		},
		status: {
			...light.colors.status,
			bg: lightNav ? theme.colors.primary.DEFAULT + "30" : "#00000050",
			bgLight: lightNav ? "#FFF" : "#00000030",
			text: lightNav ? "#111" : "#FFF",
			wm: lightNav ? "#00000050" : "#FFFFFF50",
			wmLight: lightNav ? "#00000030" : "#FFFFFF25",
			title: lightNav ? theme.colors.primary.dark : "#FFD93B",
			borderRightColor: lightNav ? "#FFF" : "#00000050",
		},
		logo: { text: lightNav ? "#fff" : "primary.dark" },
	};
	return theme;
};

const withoutBrand = (theme) => {
	const { brand: _brand, ...colors } = theme.colors;
	return { ...theme, colors };
};

const orgCases = [
	["no org", null],
	["org without theme", { metadata: {} }],
	...colorThemes.flatMap((preset) => [
		[`${preset.name} (dark nav)`, { metadata: { theme: preset } }],
		[
			`${preset.name} (light nav)`,
			{ metadata: { theme: { ...preset, navstyle: "light" } } },
		],
	]),
	["only primary set", { metadata: { theme: { primary: "#821f50" } } }],
	["invalid colors", { metadata: { theme: { primary: "red" } } }],
];

describe("buildOrgTheme", () => {
	it.each(orgCases)("matches the old _app.tsx theme for %s", (_name, org) => {
		expect(withoutBrand(buildOrgTheme(org, light))).toEqual(
			legacyTheme(org)
		);
	});

	it.each(orgCases.filter(([, org]) => org !== null))(
		"gives a readable brand foreground for %s",
		(_name, org) => {
			const { brand } = buildOrgTheme(org, light).colors;
			if (brand === PLATFORM_BRAND) return; // invalid colors fall back
			expect(contrastRatio(brand.fg, brand.solid)).toBeGreaterThanOrEqual(
				4.5
			);
		}
	);

	it("uses the platform brand on platform hosts", () => {
		expect(
			buildOrgTheme(null, light, { platform: true }).colors.brand
		).toBe(PLATFORM_BRAND);
	});

	it("does not change the base theme object", () => {
		const before = JSON.stringify(light.colors.primary);
		buildOrgTheme(
			{ metadata: { theme: { ...colorThemes[5], navstyle: "light" } } },
			light
		);
		expect(JSON.stringify(light.colors.primary)).toBe(before);
	});
});

describe("computeBrandTokens", () => {
	it("keeps a primary that has a readable foreground", () => {
		const brand = computeBrandTokens("#11299E", "#091968");
		expect(brand.solid).toBe("#11299E");
		expect(brand.fg).toBe("#FFFFFF");
		expect(brand.solidHover).toBe("#091968");
	});

	it("picks ink text on a light primary", () => {
		const brand = computeBrandTokens("#FFD93B", "#C9A400");
		expect(brand.fg).toBe("#0A0A0A");
		expect(contrastRatio(brand.fg, brand.solid)).toBeGreaterThanOrEqual(
			4.5
		);
	});

	it("falls back to primary_dark when the primary has no readable foreground", () => {
		// #777777 is below 4.5:1 with both white and ink
		expect(contrastRatio("#777777", "#FFFFFF")).toBeLessThan(4.5);
		expect(contrastRatio("#777777", "#0A0A0A")).toBeLessThan(4.5);
		const brand = computeBrandTokens("#777777", "#333333");
		expect(brand.solid).toBe("#333333");
		expect(contrastRatio(brand.fg, brand.solid)).toBeGreaterThanOrEqual(
			4.5
		);
	});

	it("returns a light tint for the subtle background", () => {
		const brand = computeBrandTokens("#11299E", "#091968");
		expect(contrastRatio(brand.subtle, "#FFFFFF")).toBeLessThan(1.3);
	});

	it("returns the platform brand for invalid colors", () => {
		expect(computeBrandTokens("red", "#000000")).toBe(PLATFORM_BRAND);
	});
});
