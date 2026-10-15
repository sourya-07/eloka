package in.eko.eloka.gateway.internal.web;

import java.io.IOException;
import in.eko.eloka.gateway.internal.dispatch.EpsDispatcher;
import in.eko.eloka.gateway.internal.envelope.EpsRequest;
import in.eko.eloka.gateway.internal.envelope.EpsRequestParser;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.shared.security.CallerContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Single entry point for every EPS-format URL (see docs/eps-compat/CONTRACT.md). */
@RestController
class EpsGatewayController {

	private final EpsRequestParser parser;
	private final EpsDispatcher dispatcher;
	private final EpsResponseWriter writer;

	EpsGatewayController(EpsRequestParser parser, EpsDispatcher dispatcher, EpsResponseWriter writer) {
		this.parser = parser;
		this.dispatcher = dispatcher;
		this.writer = writer;
	}

	@RequestMapping(
			path = { "/", "/transactions/do", "/transactions/dojson", "/transactions/wlc", "/transactions/getip",
					"/transactions/upload", "/transactions/customupload", "/authentication/{action}", "/ekoicici/**",
					"/reports/**" },
			method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE })
	ResponseEntity<?> handle(HttpServletRequest request) throws IOException {
		EpsRequest eps = parser.parse(request);
		EpsResponse response = dispatcher.dispatch(eps, CallerContext.current(), request);
		return writer.write(response, request);
	}
}
