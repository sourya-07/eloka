import { menuAnatomy } from "@chakra-ui/anatomy";
import { createMultiStyleConfigHelpers } from "@chakra-ui/react";

const { definePartsStyle, defineMultiStyleConfig } =
	createMultiStyleConfigHelpers(menuAnatomy.keys);

const baseStyle = definePartsStyle({
	list: {
		minW: "200px",
		border: "1px solid",
		borderColor: "border.default",
		boxShadow: "menu",
		borderRadius: "lg", // 14px
		bg: "bg.surface",
		py: 1.5,
		zIndex: 100,
	},
	item: {
		color: "text.primary",
		fontSize: "sm",
		fontWeight: "normal",
		minHeight: "36px",
		bg: "bg.surface",
		_hover: { bg: "bg.subtle" },
		_focus: { bg: "bg.subtle" },
	},
});

const primary = definePartsStyle({});
const accent = definePartsStyle({});
const primary_outline = definePartsStyle({});
const accent_outline = definePartsStyle({});
const ghost = definePartsStyle({});

export const menuTheme = defineMultiStyleConfig({
	baseStyle,
	variants: {
		primary,
		accent,
		primary_outline,
		accent_outline,
		ghost,
	},
});
