import { Flex } from "@chakra-ui/react";
import { usePubSub } from "@/contexts";
import { useAppSource } from "@/contexts/AppSourceContext";
import { useOrgDetailContext } from "@/contexts/OrgDetailContext";
import { useSession, useUser } from "@/contexts/UserContext";
import { fetcher } from "@/lib/apiHelper";
import useRefreshToken from "@/hooks/useRefreshToken";
import router from "next/router";
import { useCallback, useEffect, useMemo, useRef } from "react";
import { OnboardingWidget } from "../../components";
import type { OnboardingServices } from "../../contracts";

/**
 * An Onboarding component for self-onboarding of users.
 * Uses the OnboardingWidget with onboarding specific configuration.
 * @returns {JSX.Element} The rendered Onboarding component
 * @example
 * ```tsx
 * <Onboarding />
 * ```
 */
const Onboarding = () => {
	const { userData, updateUserInfo, patchUserStore } = useUser();
	console.log("[AgentOnboarding] userData", userData);
	const { accessToken } = useSession();
	const { generateNewToken } = useRefreshToken();
	const { isAndroid } = useAppSource();
	const pubsub = usePubSub();
	const { orgDetail } = useOrgDetailContext();

	// Build the services object for the onboarding feature
	const services: OnboardingServices = useMemo(
		() => ({
			accessToken,
			generateNewToken,
			isAndroid,
			pubsub,
		}),
		[accessToken, generateNewToken, isAndroid, pubsub]
	);

	const isSelfOnboardingDisabled =
		orgDetail?.metadata?.disable_self_onboarding?.value ?? false;

	const profileFetchedRef = useRef(false);

	// Method to refresh user profile and update states using the user detail API.
	// Called after partial account creation and after KYC steps that modify the backend profile.
	// Uses interaction_type_id 151 (same as post-OTP login) because new users have only an
	// OTP verification token — no refresh_token — so REFRESH_PROFILE cannot be used here.
	const refreshAgentProfile = useCallback(async () => {
		try {
			const mobile =
				userData?.userDetails?.mobile ||
				userData?.userDetails?.signup_mobile;
			const orgId =
				userData?.userDetails?.org_id || orgDetail?.org_id || "";

			const res = await fetcher(
				"/api/transaction",
				{
					token: accessToken,
					body: {
						interaction_type_id: "151",
						bc: "26",
						mobile: String(mobile || ""),
						source: "WLC",
						version: "v2",
						client_ref_id: Date.now().toString(),
						user_code: "99029899",
						user_identity_type: "mobile_number",
						initiator_id: "1234567891",
						org_id: orgId,
						user_identity: String(mobile || ""),
						id_type: "Mobile",
						lang: "null",
						email: "",
						realsourceip: "",
					},
				},
				generateNewToken
			);

			// response_type_id 369 means user exists and profile data is present
			if (res?.response_type_id === 369) {
				const userDetail = res?.data?.user_detail;
				if (userDetail) {
					patchUserStore({
						onboarding_steps: userDetail.onboarding_steps,
						onboarding: userDetail.onboarding,
						user_type: userDetail.user_type,
						role_list: userDetail.role_list,
						user_code: userDetail.code,
					});
				}

				// Redirect to home once onboarding is fully complete (onboarding flag becomes 0)
				if (
					userDetail?.onboarding !== 1 &&
					userDetail?.onboarding !== undefined &&
					userDetail?.onboarding !== null
				) {
					router.push("/home");
				}
			}

			return res;
		} catch (err) {
			console.log("[Onboarding] Error refreshing agent profile:", err);
		}
	}, [
		accessToken,
		userData?.userDetails?.mobile,
		userData?.userDetails?.signup_mobile,
		userData?.userDetails?.org_id,
		orgDetail?.org_id,
		generateNewToken,
		patchUserStore,
	]);

	// Fetch profile once — deferred until accessToken is available because
	// UserContext restores the session from sessionStorage asynchronously.
	// The ref guard prevents re-running on token refreshes mid-KYC.
	useEffect(() => {
		if (!accessToken || profileFetchedRef.current) return;
		profileFetchedRef.current = true;
		refreshAgentProfile();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [accessToken]);

	// MARK: JSX
	return (
		<Flex direction="column" minH="100vh" bg="bg">
			<OnboardingWidget
				userData={userData}
				updateUserInfo={updateUserInfo}
				isAssistedOnboarding={false}
				refreshAgentProfile={refreshAgentProfile}
				services={services}
				orgMetadataOnboarding={orgDetail?.metadata?.onboarding}
				isSelfOnboardingDisabled={isSelfOnboardingDisabled}
			/>
		</Flex>
	);
};

export default Onboarding;
