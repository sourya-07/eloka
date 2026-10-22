import { defineStyle, defineStyleConfig } from "@chakra-ui/react";

const baseStyle = defineStyle({
	px: 2, // 8px horizontal — was 3 (12px)
	py: 0.5, // 2px vertical — was 1 (4px)
	borderRadius: "full", // pills allowed for badges
	fontSize: "xs", // 12px
	fontWeight: "medium",
	textTransform: "none",
	userSelect: "none",
});

// Brand / org color — preserved
const variantPrimary = defineStyle({
	bg: "primary.DEFAULT",
	color: "white",
});

// Muted — neutral gray
const variantMuted = defineStyle({
	bg: "shade",
	color: "light",
});

// Status variants — tinted (not solid fill), using semantic tokens
const variantSuccess = defineStyle({
	bg: "success.bg",
	color: "success.fg",
});

const variantError = defineStyle({
	bg: "danger.bg",
	color: "danger.fg",
});

const variantWarning = defineStyle({
	bg: "warning.bg",
	color: "warning.fg",
});

const variantInfo = defineStyle({
	bg: "info.bg",
	color: "info.fg",
});

const variantHighlight = defineStyle({
	bg: "highlight",
	color: "dark",
});

// Outline variants — for contexts needing even lighter treatment
const variantOutlinePrimary = defineStyle({
	bg: "white",
	color: "primary.DEFAULT",
});

const variantOutlineSuccess = defineStyle({
	bg: "white",
	color: "success.fg",
});

const variantOutlineError = defineStyle({
	bg: "white",
	color: "danger.fg",
});

const variantOutlineHighlight = defineStyle({
	bg: "white",
	color: "highlight",
});

export const badgeTheme = defineStyleConfig({
	baseStyle,
	variants: {
		primary: variantPrimary,
		muted: variantMuted,
		success: variantSuccess,
		error: variantError,
		warning: variantWarning,
		info: variantInfo,
		highlight: variantHighlight,
		outlineSuccess: variantOutlineSuccess,
		outlineError: variantOutlineError,
		outlinePrimary: variantOutlinePrimary,
		outlineHighlight: variantOutlineHighlight,
	},
});
