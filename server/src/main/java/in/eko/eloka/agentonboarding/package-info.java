/**
 * Agent onboarding (KYC) for retailers and distributors: the step plan per user type, which
 * steps are still pending, and the KYC data each step collects. Personal data (PAN, bank
 * account) is encrypted; images live in the documents module. The Aadhaar number itself is
 * never collected or stored.
 *
 * <p>Public API: {@link in.eko.eloka.agentonboarding.AgentOnboardingApi}.
 */
@ApplicationModule(displayName = "Agent onboarding")
package in.eko.eloka.agentonboarding;

import org.springframework.modulith.ApplicationModule;
