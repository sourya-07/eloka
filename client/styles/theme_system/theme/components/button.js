import { defineStyle, defineStyleConfig } from "@chakra-ui/react";

// ── Preserved variants — keep backward compat with existing variant="primary" / "accent" usage ──

const primary = defineStyle({
	bg: "primary.DEFAULT",
	color: "white",
	_hover: {
		bg: "primary.dark",
		_disabled: { bg: "primary.DEFAULT" },
	},
});

const accent = defineStyle({
	bg: "accent.DEFAULT",
	color: "white",
	_hover: {
		bg: "accent.dark",
		_disabled: { bg: "accent.DEFAULT" },
	},
});

const success = defineStyle({
	bg: "success",
	color: "white",
	_hover: {
		bg: "#00a336",
		_disabled: { bg: "success" },
	},
});

const primary_outline = defineStyle({
	border: "2px solid",
	borderColor: "primary.DEFAULT",
	color: "primary.DEFAULT",
	_hover: {
		color: "white",
		bg: "primary.DEFAULT",
	},
});

const accent_outline = defineStyle({
	border: "2px solid",
	borderColor: "accent.DEFAULT",
	color: "accent.DEFAULT",
	_hover: {
		color: "white",
		bg: "accent.DEFAULT",
	},
});

const link = defineStyle({
	color: "dark",
	textDecoration: "none",
	_hover: { textDecoration: "none" },
	_active: { textDecoration: "none" },
	_focusVisible: { boxShadow: "none", color: "dark" },
});

// ── New fintech variants ──

// Primary CTA — filled near-black. Default for unspecified variant.
const cta = defineStyle({
	bg: "cta.bg",
	color: "cta.fg",
	_hover: {
		bg: "cta.bgHover",
		_disabled: { bg: "cta.bg" },
	},
	_active: { opacity: 0.88 },
	_disabled: { opacity: 0.4 },
});

// Secondary — white surface with border
const secondary = defineStyle({
	bg: "bg.surface",
	color: "text.primary",
	border: "1px solid",
	borderColor: "border.strong",
	_hover: {
		bg: "bg.subtle",
		_disabled: { bg: "bg.surface" },
	},
});

// Ghost — no background, subtle hover
const ghost = defineStyle({
	bg: "transparent",
	color: "text.primary",
	_hover: { bg: "bg.subtle" },
	_active: { bg: "bg.subtle" },
});

// Danger — red fill
const danger = defineStyle({
	bg: "red.500",
	color: "white",
	_hover: {
		bg: "red.700",
		_disabled: { bg: "red.500" },
	},
});

export const buttonTheme = defineStyleConfig({
	variants: {
		primary,
		accent,
		success,
		cta,
		secondary,
		ghost,
		danger,
		link,
		outline: accent_outline,
		accent_outline,
		primary_outline,
	},
	baseStyle: {
		borderRadius: "md", // resolves to 10px via theme radii
		fontWeight: "medium", // 500
	},
	sizes: {
		sm: { h: "32px", fontSize: "sm", px: 4 },
		md: { h: "40px", fontSize: "sm", px: 5 },
		lg: { h: "40px", fontSize: "sm", px: 6 }, // capped at 40px
	},
	defaultProps: {
		variant: "cta",
	},
});
