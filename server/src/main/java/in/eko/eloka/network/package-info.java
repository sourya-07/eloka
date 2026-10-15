/**
 * Users of a tenant and their network: retailers, distributors and org admins, their
 * profile, shop and (from P3) the distributor hierarchy.
 *
 * <p>Status: users and profiles built (P1). Public API: {@link in.eko.eloka.network.UserDirectory},
 * {@link in.eko.eloka.network.UserCommands}.
 */
@ApplicationModule(displayName = "Network")
package in.eko.eloka.network;

import org.springframework.modulith.ApplicationModule;
