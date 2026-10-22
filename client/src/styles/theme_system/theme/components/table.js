import { defineStyle, defineStyleConfig } from "@chakra-ui/react";

// No even-row stripe, white header, hover bg.subtle on rows
const stripedActionNone = defineStyle({
	tr: {
		_even: {
			background: "transparent", // no stripe fill
		},
	},
	th: {
		textTransform: "none", // sentence case enforced in copy, not CSS
		fontWeight: "medium",
		fontSize: "xs",
		color: "text.secondary",
		bg: "bg.surface", // white header — no gray fill
		borderBottom: "1px solid",
		borderBottomColor: "border.default",
		transition: "none",
	},
});

const stripedActionRedirect = defineStyle({
	tr: {
		_even: {
			background: "transparent",
		},
		_hover: {
			bg: "bg.subtle",
			transition: "background 150ms ease-in",
			cursor: "pointer",
		},
	},
	th: {
		textTransform: "none",
		fontWeight: "medium",
		fontSize: "xs",
		color: "text.secondary",
		bg: "bg.surface",
		borderBottom: "1px solid",
		borderBottomColor: "border.default",
		transition: "none",
	},
});

const stripedActionExpand = defineStyle({
	tr: {
		_even: {
			background: "transparent",
		},
		_hover: {
			cursor: "pointer",
		},
	},
	th: {
		textTransform: "none",
		fontWeight: "medium",
		fontSize: "xs",
		color: "text.secondary",
		bg: "bg.surface",
		borderBottom: "1px solid",
		borderBottomColor: "border.default",
		transition: "none",
	},
});

export const tableTheme = defineStyleConfig({
	variants: {
		stripedActionNone,
		stripedActionRedirect,
		stripedActionExpand,
	},
});
