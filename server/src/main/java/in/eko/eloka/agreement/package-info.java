/**
 * Agreement templates, rendering, e-sign sessions and signed-PDF archive.
 *
 * <p>Built in P7 (docs/onboarding/P7_APPLICANT_API.md): PDF rendering with OpenPDF, e-sign through
 * {@code integration.EsignProvider}.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Agreement")
package in.eko.eloka.agreement;

import org.springframework.modulith.ApplicationModule;
