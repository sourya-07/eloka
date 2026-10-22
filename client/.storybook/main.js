const path = require("path");
import remarkGfm from "remark-gfm";

module.exports = {
	stories: ["../src/**/*.mdx", "../src/**/*.stories.@(js|jsx|ts|tsx)"],

	addons: [
		"@storybook/addon-links",
		"@storybook/addon-essentials",
		"@storybook/addon-interactions",
		// "@storybook/addon-postcss",
		"@chakra-ui/storybook-addon",
		// {
		// 	name: "@storybook/addon-storysource",
		// 	// See all options here:
		// 	//   https://github.com/storybookjs/storybook/blob/main/lib/source-loader/README.md#options
		// 	options: {
		// 		rule: {
		// 			// test: [/\.stories\.jsx?$/], This is default
		// 			include: [
		// 				path.resolve(__dirname, "../src"),
		// 			], // You can specify directories
		// 		},
		// 		loaderOptions: {
		// 			prettierConfig: { printWidth: 80, singleQuote: false },
		// 		},
		// 	},
		// },
		{
			name: "@storybook/addon-docs",
			options: {
				mdxPluginOptions: {
					mdxCompileOptions: {
						remarkPlugins: [remarkGfm],
					},
				},
			},
		},
		"@storybook/addon-mdx-gfm",
		"@chromatic-com/storybook",
	],

	features: {
		emotionAlias: false,
	},

	framework: {
		name: "@storybook/nextjs",
		options: {},
	},

	staticDirs: ["../public"],

	docs: {},

	core: {
		disableTelemetry: true,
	},

	// typescript: {
	// 	check: false,
	// 	checkOptions: {},
	// 	reactDocgen: false
	// },
	webpackFinal: async (config) => {
		// Same "@/…" alias as tsconfig.json
		config.resolve.alias["@"] = path.resolve(__dirname, "../src");
		config.module.rules.push({
			test: /\.mjs$/,
			include: /node_modules/,
			type: "javascript/auto",
		});
		return config;
	},
};
