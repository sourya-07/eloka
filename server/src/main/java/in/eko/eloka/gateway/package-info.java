/**
 * The EPS-compatible API: accepts the request format the client was written for (interaction
 * envelope, "tf-req" REST tunnel, multipart uploads) and translates it into calls on the
 * domain modules. Nothing depends on this module; it depends on the modules it serves.
 *
 * <p>Contract: {@code docs/eps-compat/CONTRACT.md}. Adding a call = one {@code EpsHandler}
 * bean in {@code internal.handler.*} plus a contract test.
 */
@ApplicationModule(displayName = "EPS gateway")
package in.eko.eloka.gateway;

import org.springframework.modulith.ApplicationModule;
