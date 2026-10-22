import { extendTheme, theme } from "@chakra-ui/react";
import { globalStyles } from "./globals";
import {
	badgeTheme,
	buttonTheme,
	checkboxTheme,
	radioTheme,
	switchTheme,
	tableTheme,
	tabsTheme,
	menuTheme,
} from "./theme_system/theme/components";

export const light = extendTheme({
	initialColorMode: "light",
	useSystemColorMode: false,
	breakpoints: {
		base: "0",
		sm: "440px",
		md: "768px",
		lg: "1024px",
		xl: "1200px",
		"2xl": "1800px",
	},
	styles: {
		global: globalStyles,
	},
	// Semantic tokens — consumed as bg="bg.canvas", color="text.secondary", etc.
	// These layer on top of primitive colors; primitive refs (color="success") still work.
	semanticTokens: {
		colors: {
			// Canvas & surface
			"bg.canvas": { default: "#FAFAF7" },
			"bg.surface": { default: "#FFFFFF" },
			"bg.subtle": { default: "#F4F3EE" },
			"bg.inverse": { default: "#0A0A0A" },
			// Text (secondary and tertiary pass 4.5:1 on canvas, surface and subtle)
			"text.primary": { default: "#0A0A0A" },
			"text.secondary": { default: "#5C5C57" },
			"text.tertiary": { default: "#6F6F6A" },
			"text.inverse": { default: "#FFFFFF" },
			"text.link": { default: "#11299E" },
			// Border
			"border.default": { default: "#E5E5E0" },
			"border.strong": { default: "#D4D4CE" },
			// Edge of inputs, selects and checkboxes (3:1 against white, WCAG 1.4.11)
			"border.input": { default: "#85857F" },
			// Focus ring and modal scrim
			"focus.ring": { default: "#11299E" },
			overlay: { default: "rgba(10,10,10,0.48)" },
			// CTA — the single black primary action
			"cta.bg": { default: "#0A0A0A" },
			"cta.bgHover": { default: "#1F1F1F" },
			"cta.fg": { default: "#FFFFFF" },
			// Semantic state palettes (tinted, not solid-fill)
			"success.bg": { default: "#ECFDF5" },
			"success.fg": { default: "#065F46" },
			"success.border": { default: "#A7F3D0" },
			"danger.bg": { default: "#FEF2F2" },
			"danger.fg": { default: "#991B1B" },
			"danger.border": { default: "#FCA5A5" },
			"warning.bg": { default: "#FFFBEB" },
			"warning.fg": { default: "#92400E" },
			"warning.border": { default: "#FCD34D" },
			"info.bg": { default: "#EFF6FF" },
			"info.fg": { default: "#1E40AF" },
			"info.border": { default: "#BFDBFE" },
			"neutral.bg": { default: "#F4F3EE" },
			"neutral.fg": { default: "#5C5C57" },
			"neutral.border": { default: "#E5E5E0" },
		},
	},
	colors: {
		navstyle: "default",
		// primary & accent are preserved for org-overrides and EkoConnect widget color sync
		primary: {
			light: "#1F3ABC", // ORIG_THEME: #1d3ac9
			DEFAULT: "#11299E",
			dark: "#091968", // ORIG_THEME: #0d2289
		},
		accent: {
			light: "#ffad23",
			DEFAULT: "#FE9F00",
			dark: "#f38300",
		},
		success: "#00C341",
		error: "#FF4081",
		divider: "#E9EDF1",
		hint: "#D2D2D2",
		dark: "#0F0F0F",
		light: "#555555",
		focusbg: "#FFFBF3",
		white: "#FFFFFF",
		shade: "#F2F2F2",
		darkShade: "#e6e6e6",
		highlight: "#FFD93B",
		bg: "#d7dbe3",
		inputLabel: "#0C243B",
		overlayBg: "#e9edf1b3",
	},
	components: {
		Badge: badgeTheme,
		Button: buttonTheme,
		Checkbox: checkboxTheme,
		Radio: radioTheme,
		Switch: switchTheme,
		Table: tableTheme,
		Tabs: tabsTheme,
		Menu: menuTheme,
		Skeleton: {
			baseStyle: {
				borderRadius: "4px",
			},
		},
		FormLabel: {
			baseStyle: {
				fontSize: "xs", // 12px
				fontWeight: "medium", // 500
				pl: "0",
				mb: 2,
				color: "text.secondary",
			},
		},
		Input: {
			variants: {
				outline: {
					field: {
						h: "40px",
						borderRadius: "md",
						borderColor: "border.default",
						bg: "bg.surface",
						_hover: { borderColor: "border.strong" },
						_focus: {
							borderColor: "text.primary",
							boxShadow: "focus",
							bg: "bg.surface",
						},
						_invalid: {
							borderColor: "error",
							boxShadow: "none",
						},
					},
				},
			},
			defaultProps: {
				variant: "outline",
			},
		},
		NumberInput: {
			variants: {
				outline: {
					field: {
						h: "40px",
						borderRadius: "md",
						borderColor: "border.default",
						bg: "bg.surface",
						_hover: { borderColor: "border.strong" },
						_focus: {
							borderColor: "text.primary",
							boxShadow: "focus",
							bg: "bg.surface",
						},
					},
				},
			},
			defaultProps: {
				variant: "outline",
			},
		},
		PinInput: {
			baseStyle: {
				w: "56px",
				h: "64px",
				borderRadius: "md",
				fontSize: "xl",
				fontWeight: "medium",
				borderColor: "border.default",
				bg: "bg.surface",
				_focus: {
					borderColor: "border.strong",
					boxShadow: "focus",
					bg: "bg.surface",
				},
			},
		},
		// Alert baseStyle affects inline Alerts AND toasts (toasts render Alert internally)
		Alert: {
			baseStyle: {
				container: {
					borderRadius: "md",
					px: 3,
					py: 2.5,
				},
				title: {
					fontWeight: "medium",
					fontSize: "sm",
				},
				description: {
					fontSize: "xs",
				},
			},
			variants: {
				// Override subtle so toasts use white bg + neutral border + colored icon
				subtle: {
					container: {
						bg: "bg.surface",
						border: "1px solid",
						borderColor: "border.default",
					},
				},
			},
		},
		Heading: {
			variants: {
				selectNone: {
					...theme.components.Heading,
					userSelect: "none",
				},
			},
		},
		Text: {
			baseStyle: {
				userSelect: "none",
			},
			variants: {
				selectNone: {
					userSelect: "none",
				},
			},
		},
	},
	borders: {
		none: 0,
		"1px": "1px solid",
		"2px": "0.125rem solid",
		"10px": "0.625rem solid",
		basic: "1px solid #E9EDF1",
		card: "1px solid #D2D2D2",
		"br-sidebar": "1px solid #1F3ABC",
		"br-popupcard": "1px solid #FE9F00",
	},
	radii: {
		none: "0",
		base: "0.25rem",
		sm: "6px", // was 2px
		md: "10px", // was 6px
		lg: "14px", // was 8px
		xl: "18px", // was 12px
		"2xl": "1rem",
		"3xl": "1.5rem",
		full: "9999px",
		// Numeric shortcuts preserved for backward compat
		5: "5px",
		10: "10px",
		15: "15px",
		20: "20px",
	},
	shadows: {
		// Legacy shadows — kept for backward compat
		basic: "0px 5px 15px #0000000D",
		"sh-otpfocus": "0px 3px 6px #0000001A",
		"sh-card": "0px 3px 20px #00000005",
		"sh-button": "0px 3px 10px #00000018",
		// New semantic shadows
		focus: "0 0 0 3px rgba(10,10,10,0.08)",
		menu: "0 4px 16px rgba(0,0,0,0.08)",
		// Platform screens (docs/frontend/UI_UX_PLAN.md §2.3)
		focusRing: "0 0 0 2px #FFFFFF, 0 0 0 4px #11299E",
		e1: "0 1px 2px rgba(10,10,10,0.04)",
		e2: "0 8px 24px rgba(10,10,10,0.08)",
		e3: "0 16px 48px rgba(10,10,10,0.12)",
		none: "none",
	},
	sizes: {
		max: "max-content",
		min: "min-content",
		full: "100%",
		"3xs": "14rem",
		"2xs": "16rem",
		xs: "20rem",
		sm: "24rem",
		md: "28rem",
		lg: "32rem",
		xl: "36rem",
		"2xl": "42rem",
		"3xl": "48rem",
		"4xl": "56rem",
		"5xl": "64rem",
		"6xl": "72rem",
		"7xl": "80rem",
		"8xl": "90rem",
		container: {
			sm: "640px",
			md: "768px",
			lg: "1024px",
			xl: "1280px",
		},
	},
	space: {
		px: "1px",
		0.5: "0.125rem",
		1: "0.25rem",
		1.5: "0.375rem",
		2: "0.5rem",
		2.5: "0.625rem", // 10px
		3: "0.75rem",
		3.5: "0.875rem", // 14px
		4: "1rem", // 16px
		5: "1.25rem", //20px
		6: "1.5rem", //24px
		7: "1.75rem", // 28px
		7.5: "1.875rem", // 28px - added
		8: "2rem",
		8.5: "2.125rem", // 24px- added
		9: "2.25rem",
		10: "2.5rem",
		10.5: "2.625rem", // 42px - added
		12: "3rem",
		14: "3.5rem", //56px
		16: "4rem",
		17: "4.5rem",
		20: "5rem",
		24: "6rem",
		28: "7rem",
		32: "8rem",
		36: "9rem",
		40: "10rem",
		44: "11rem",
		48: "12rem",
		52: "13rem",
		56: "14rem",
		60: "15rem",
		64: "16rem",
		72: "18rem",
		80: "20rem",
		96: "24rem",
	},
	fonts: {
		body: "Inter, system-ui, sans-serif",
		heading: "Inter, system-ui, sans-serif",
	},
	fontSizes: {
		xxs: "0.625rem",
		xs: "0.75rem", // 12px
		sm: "0.875rem", // 14px
		md: "1rem", // 16px
		lg: "1.125rem", // 18px
		xl: "1.25rem", // 20px
		"2xl": "1.5rem",
		"3xl": "1.875rem",
		"4xl": "2.25rem",
		"5xl": "3rem",
		"6xl": "3.75rem",
		"7xl": "4.5rem",
		"8xl": "6rem",
		"9xl": "8rem",
	},
	fontWeights: {
		normal: 400,
		medium: 500,
		semibold: 500, // collapsed from 600 — two weights only: 400 and 500
		bold: 500, // collapsed from 700
		extrabold: 500, // collapsed from 800
		// Real weights for the new platform screens; existing screens keep the collapse until the R1 reskin
		title: 600,
		display: 700,
	},
	lineHeights: {
		normal: "normal",
		none: 1,
		shorter: 1.25,
		short: 1.375,
		base: 1.5,
		tall: 1.625,
		taller: "2",
		"3": ".75rem",
		"4": "1rem",
		"5": "1.25rem",
		"6": "1.5rem",
		"7": "1.75rem",
		"8": "2rem",
		"9": "2.25rem",
		"10": "2.5rem",
	},
	letterSpacings: {
		...theme.letterSpacings,
	},
});
