import {
	contrastRatio,
	getFirstContrastColor,
	getHighContrastColor,
	hasMinimumContrast,
} from "@/utils/colorUtils";

describe("colorUtils contrast helpers", () => {
	describe("contrastRatio", () => {
		it("is 21 for black on white", () => {
			expect(contrastRatio("#000000", "#FFFFFF")).toBeCloseTo(21, 1);
		});

		it("is 1 for a color against itself", () => {
			expect(contrastRatio("#11299E", "#11299E")).toBeCloseTo(1, 5);
		});

		it("is symmetric", () => {
			expect(contrastRatio("#FE9F00", "#FFFFFF")).toBeCloseTo(
				contrastRatio("#FFFFFF", "#FE9F00"),
				5
			);
		});

		it("accepts 3-digit hex", () => {
			expect(contrastRatio("#000", "#fff")).toBeCloseTo(21, 1);
		});

		it("returns NaN for invalid input", () => {
			expect(contrastRatio("not-a-color", "#FFFFFF")).toBeNaN();
		});
	});

	describe("hasMinimumContrast", () => {
		it("passes white on Connect Blue", () => {
			expect(hasMinimumContrast("#FFFFFF", "#11299E")).toBe(true);
		});

		it("fails white on the default accent orange", () => {
			expect(hasMinimumContrast("#FFFFFF", "#FE9F00")).toBe(false);
		});

		it("uses the given minimum ratio", () => {
			expect(hasMinimumContrast("#FFFFFF", "#ED7802", 2.5)).toBe(true);
		});
	});

	describe("getFirstContrastColor", () => {
		it("returns the first color that passes 4.5:1", () => {
			expect(getFirstContrastColor("#FE9F00", "#FFFFFF", "#0A0A0A")).toBe(
				"#0A0A0A"
			);
		});

		it("returns null when none passes", () => {
			expect(getFirstContrastColor("#777777", "#888888")).toBeNull();
		});
	});

	describe("getHighContrastColor", () => {
		it("picks the color with more contrast", () => {
			expect(getHighContrastColor("#11299E", "#FFFFFF", "#0A0A0A")).toBe(
				"#FFFFFF"
			);
		});
	});
});
