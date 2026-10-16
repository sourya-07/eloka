package in.eko.eloka.network;

import java.util.List;
import in.eko.eloka.shared.security.Caller;

/**
 * "Onboard Agents": an admin or distributor creates accounts for new agents, who then log in
 * with their mobile and complete KYC. Who may create what:
 * <ul>
 * <li>org admin and super distributor: retailers and distributors;</li>
 * <li>distributor: retailers only, always placed under themselves.</li>
 * </ul>
 */
public interface AgentEnrolment {

	int MAX_ROWS = 5000;

	/** @param userType {@link UserTypes#MERCHANT} or {@link UserTypes#DISTRIBUTOR} */
	List<OnboardResult> onboard(Caller caller, int userType, List<AgentSignup> agents);
}
