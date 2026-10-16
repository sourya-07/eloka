package in.eko.eloka.devdata;

import in.eko.eloka.shared.config.UnsafeInProduction;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local only: the page the fake DigiLocker link opens. */
@RestController
@Profile("local")
class DevPagesController implements UnsafeInProduction {

	@GetMapping(path = "/v1/dev/digilocker", produces = MediaType.TEXT_HTML_VALUE)
	String digilocker() {
		return """
				<!doctype html><html lang="en"><head><meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1"><title>Fake DigiLocker</title>
				<style>body{font-family:system-ui,sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem;line-height:1.5}</style>
				</head><body><h1>Fake DigiLocker (local)</h1>
				<p>Consent recorded. This page stands in for DigiLocker on a developer laptop; no Aadhaar data is used.</p>
				<p>Close this tab and continue in the app.</p></body></html>""";
	}

	@Override
	public String unsafeReason() {
		return "DevPagesController serves the fake DigiLocker page";
	}
}
