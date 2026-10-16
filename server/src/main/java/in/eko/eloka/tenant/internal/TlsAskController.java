package in.eko.eloka.tenant.internal;

import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.tenant.OrgStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asked by the TLS edge (Caddy on-demand TLS) before it requests a certificate for a hostname:
 * 200 only for an active tenant domain, so nobody can make the edge issue certificates for
 * arbitrary names. Called on the internal network; it reveals no more than visiting the site.
 */
@RestController
class TlsAskController {

	private final OrgApi orgs;

	TlsAskController(OrgApi orgs) {
		this.orgs = orgs;
	}

	@GetMapping("/v1/system/tls-allowed")
	ResponseEntity<Void> allowed(@RequestParam(name = "domain", required = false) String domain) {
		boolean known = orgs.findByHostname(domain).filter(org -> org.status() == OrgStatus.ACTIVE).isPresent();
		return known ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
	}
}
