package in.eko.eloka.onboarding.internal;

import java.util.Set;
import in.eko.eloka.onboarding.StepKey;
import tools.jackson.databind.JsonNode;

/**
 * Validates and completes one data step submitted through A8
 * ({@code POST …/steps/{step_key}/submit}). Verification steps (PAN, email, KYC, bank,
 * agreement) have their own resources instead.
 */
interface StepHandler {

	StepKey key();

	/** Fields the applicant may save as a draft (A7). Personal data such as PAN never goes here. */
	Set<String> draftFields();

	void submit(StepContext context, JsonNode data);
}
