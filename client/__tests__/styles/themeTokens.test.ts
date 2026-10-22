import { light } from "@/styles/themes";
import { contrastRatio } from "@/utils/colorUtils";

const token = (name: string): string =>
	light.semanticTokens.colors[name].default;

const surfaces = ["bg.canvas", "bg.surface", "bg.subtle"];

describe("theme tokens (docs/frontend/UI_UX_PLAN.md §2)", () => {
	it.each(["text.primary", "text.secondary", "text.tertiary", "text.link"])(
		"%s passes 4.5:1 on every surface",
		(text) => {
			surfaces.forEach((surface) => {
				expect(
					contrastRatio(token(text), token(surface))
				).toBeGreaterThanOrEqual(4.5);
			});
		}
	);

	it("border.input passes 3:1 against white and canvas", () => {
		expect(
			contrastRatio(token("border.input"), token("bg.surface"))
		).toBeGreaterThanOrEqual(3);
		expect(
			contrastRatio(token("border.input"), token("bg.canvas"))
		).toBeGreaterThanOrEqual(3);
	});

	it.each(["success", "danger", "warning", "info", "neutral"])(
		"%s.fg passes 4.5:1 on its own bg",
		(tone) => {
			expect(
				contrastRatio(token(`${tone}.fg`), token(`${tone}.bg`))
			).toBeGreaterThanOrEqual(4.5);
		}
	);

	it("white text passes 4.5:1 on the CTA", () => {
		expect(
			contrastRatio(token("cta.fg"), token("cta.bg"))
		).toBeGreaterThanOrEqual(4.5);
	});

	it("keeps the legacy tenant colour keys", () => {
		expect(light.colors.primary).toEqual({
			light: "#1F3ABC",
			DEFAULT: "#11299E",
			dark: "#091968",
		});
		expect(light.colors.accent).toEqual({
			light: "#ffad23",
			DEFAULT: "#FE9F00",
			dark: "#f38300",
		});
	});

	it("keeps the collapsed legacy weights and adds the platform ones", () => {
		expect(light.fontWeights.semibold).toBe(500);
		expect(light.fontWeights.bold).toBe(500);
		expect(light.fontWeights.title).toBe(600);
		expect(light.fontWeights.display).toBe(700);
	});
});
