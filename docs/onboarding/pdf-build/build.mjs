// Builds the two ELOKA Local Admin onboarding PDFs from the Markdown docs.
// Usage: node docs/onboarding/pdf-build/build.mjs <out-dir>
// Needs Google Chrome and network access (marked + mermaid load from jsDelivr).
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const DOCS = resolve(HERE, "..");
const OUT = resolve(process.argv[2] || join(HERE, "out"));
const CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
mkdirSync(OUT, { recursive: true });

const read = (p) => readFileSync(p, "utf8");

const BLUEPRINT = {
	file: "ELOKA_Local_Admin_Onboarding_Blueprint",
	page: "A4 landscape",
	title: "ELOKA Local Admin Onboarding",
	subtitle:
		"Backend Architecture &amp; Implementation Blueprint — Phase 0 (design only)",
	status:
		"Status: DRAFT for architecture review · Generated 5 October 2026 · Revised 14 October 2026 (Java 25 + Spring Boot 4, MySQL 8.4) · Source: eloka/docs/onboarding/",
	numberedToc: true,
	sections: [
		["Executive Summary", join(HERE, "EXECUTIVE_SUMMARY.md")],
		["Onboarding Journey", join(DOCS, "ELOKA_ONBOARDING_JOURNEY.md")],
		["Frontend → Backend Mapping", join(DOCS, "FRONTEND_BACKEND_MAPPING.md")],
		["State Machine", join(DOCS, "ONBOARDING_STATE_MACHINE.md")],
		["Architecture", join(DOCS, "ARCHITECTURE.md")],
		["Database Design", join(DOCS, "DATABASE_DESIGN.md")],
		["API Specification", join(DOCS, "API_SPECIFICATION.md")],
		["Integrations", join(DOCS, "INTEGRATIONS.md")],
		["Security", join(DOCS, "SECURITY.md")],
		["Implementation Plan", join(DOCS, "IMPLEMENTATION_PLAN.md")],
		["Open Questions", join(DOCS, "OPEN_QUESTIONS.md")],
	],
};

const guideMd = read(join(HERE, "SIMPLE_GUIDE.md"));
const GUIDE = {
	file: "ELOKA_Local_Admin_Onboarding_Simple_Guide",
	page: "A4 portrait",
	title: "ELOKA Local Admin Onboarding",
	subtitle: "Simple Guide — the short, easy version of the full blueprint",
	status:
		"Status: DRAFT for review · 6 October 2026 · Updated 14 October 2026 (Java + Spring Boot, MySQL) · Plan only, no code written yet",
	numberedToc: false,
	// The guide is one file; its "# n. Title" headings form the contents list.
	toc: [...guideMd.matchAll(/^# (.+)$/gm)].map((m) => m[1]),
	sections: [[null, join(HERE, "SIMPLE_GUIDE.md")]],
};

function html(doc) {
	const parts = doc.sections.map(([, p]) => read(p));
	const toc = doc.toc || doc.sections.map(([t]) => t);
	const tocHtml = toc
		.map((t, i) =>
			doc.numberedToc
				? `<li><b>${i + 1}.</b> ${t}</li>`
				: `<li>${t}</li>`
		)
		.join("");
	return `<!doctype html><html><head><meta charset="utf-8"><title>building</title>
<style>
@page { size: ${doc.page}; margin: 16mm 18mm 18mm; }
* { box-sizing: border-box; }
body { font-family: "Helvetica Neue", Helvetica, Arial, sans-serif; font-size: ${doc.page.includes("landscape") ? "11pt" : "10.5pt"}; line-height: 1.45; color: #1d2433; margin: 0; }
.cover { height: 170mm; display: flex; flex-direction: column; justify-content: center; page-break-after: always; }
.cover h1 { font-size: 30pt; color: #123c8c; margin: 0 0 14px; border: 0; }
.cover .sub { font-size: 13pt; color: #3a4252; margin-bottom: 10px; }
.cover .status { font-size: 8.5pt; color: #6b7280; margin-bottom: 18px; }
.cover ol { columns: 2; list-style: none; padding: 0; margin: 0; font-size: 9.5pt; }
.cover li { margin: 3px 0; break-inside: avoid; }
.cover li b { color: #123c8c; display: inline-block; min-width: 22px; }
.section { page-break-before: always; }
.section:first-of-type { page-break-before: auto; }
h1 { color: #123c8c; font-size: 19pt; border-bottom: 2px solid #123c8c; padding-bottom: 4px; margin: 18px 0 10px; break-after: avoid; }
h2 { color: #123c8c; font-size: 14pt; margin: 16px 0 8px; break-after: avoid; }
h3 { color: #123c8c; font-size: 11.5pt; margin: 14px 0 6px; break-after: avoid; }
h4 { font-size: 10.5pt; margin: 12px 0 6px; break-after: avoid; }
.guide h1 { font-size: 17pt; margin-top: 22px; }
p, li { orphans: 3; widows: 3; }
table { border-collapse: collapse; width: 100%; margin: 8px 0 12px; font-size: 0.92em; }
th, td { border: 1px solid #d0d5dd; padding: 4px 6px; text-align: left; vertical-align: top; }
th { background: #eef1f6; }
tr { break-inside: avoid; }
code { font-family: Menlo, Consolas, monospace; font-size: 0.86em; background: #f2f4f7; padding: 0 3px; border-radius: 3px; }
pre { background: #f6f8fa; border: 1px solid #e3e6eb; padding: 8px 10px; font-size: 0.85em; white-space: pre-wrap; break-inside: avoid; }
pre code { background: none; padding: 0; }
blockquote { margin: 8px 0; padding: 6px 12px; border-left: 4px solid #123c8c; background: #f3f6fc; color: #2b3445; }
.mermaid { text-align: center; margin: 10px 0; break-inside: avoid; }
.mermaid svg { max-width: 100% !important; height: auto; max-height: ${doc.page.includes("landscape") ? "165mm" : "235mm"}; }
.page-break { page-break-after: always; }
hr { border: 0; border-top: 1px solid #d0d5dd; margin: 14px 0; }
a { color: #123c8c; text-decoration: none; }
</style></head><body class="${doc.numberedToc ? "" : "guide"}">
<div class="cover"><h1>${doc.title}</h1><div class="sub">${doc.subtitle}</div><div class="status">${doc.status}</div><ol>${tocHtml}</ol></div>
<div id="content"></div>
<script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
<script>
const parts = ${JSON.stringify(parts)};
const root = document.getElementById("content");
for (const md of parts) {
  const s = document.createElement("div");
  s.className = "section";
  s.innerHTML = marked.parse(md, { gfm: true });
  root.appendChild(s);
}
document.querySelectorAll("pre code.language-mermaid").forEach((c) => {
  const d = document.createElement("div");
  d.className = "mermaid";
  d.textContent = c.textContent;
  c.parentElement.replaceWith(d);
});
mermaid.initialize({ startOnLoad: false, theme: "default", securityLevel: "loose", flowchart: { useMaxWidth: true, htmlLabels: true }, er: { useMaxWidth: true }, sequence: { useMaxWidth: true } });
mermaid.run({ querySelector: ".mermaid" })
  .then(() => { document.title = "READY"; })
  .catch((e) => { document.title = "MERMAID_ERROR"; console.error(e); });
</script></body></html>`;
}

for (const doc of [BLUEPRINT, GUIDE]) {
	const htmlPath = join(OUT, `${doc.file}.html`);
	const pdfPath = join(OUT, `${doc.file}.pdf`);
	writeFileSync(htmlPath, html(doc));
	execFileSync(CHROME, [
		"--headless=new",
		"--disable-gpu",
		"--no-pdf-header-footer",
		"--virtual-time-budget=90000",
		"--run-all-compositor-stages-before-draw",
		`--print-to-pdf=${pdfPath}`,
		`file://${htmlPath}`,
	]);
	console.log("built", pdfPath);
}
