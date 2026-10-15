import { useToast } from "@chakra-ui/react";
import { useAppSource, useOrgDetailContext, useUser } from "contexts";
import { fetcher } from "helpers/apiHelper";
import { useState } from "react";
import { parseEnvBoolean } from "utils/envUtils";

/**
 * A custom hook to submit login request to the server process the login response (user profile data, tokens, etc)
 * @param {Function} login - The login function to call
 * @param {Function} setStep - Function to set the current step in the login process
 * @param {Function} setEmail - Function to set the email address for social login
 * @param {Function} setCachedSocialResponse - Function to set the cached social login response (used for mobile verification in social login)
 * @returns {[boolean, Function]} - Array containing busy state and submitLogin function
 */
function useLogin(login, setStep, setEmail, setCachedSocialResponse) {
	const { login: processLoginResponse } = useUser();
	const [busy, setBusy] = useState(false);
	const toast = useToast();
	const { isAndroid } = useAppSource();

	const { orgDetail } = useOrgDetailContext();
	const { metadata } = orgDetail || {};
	const { disable_self_onboarding } = metadata || {};

	const isSelfOnboardingDisabled =
		disable_self_onboarding?.value ||
		parseEnvBoolean(process.env.NEXT_PUBLIC_DISABLE_SELF_ONBOARDING) ||
		false;

	/**
	 * Login the user by fetching user profile & tokens.
	 * If successful set the login & user profile state of the app.
	 * @param {object} data The user data & other details
	 * @param {string} data.id_type The login type (Mobile/Google)
	 * @param {string} data.id_token The login id token for validation (OTP or Gmail token)
	 * @param {string} data.mobile The user's mobile number (for Mobile login)
	 * @param {string} data.org_id The organization ID
	 * @param {string} data.org_token A JWT token with org details (including org_id)
	 * @param {string} data.google_token_type The type of Google token ("credential" or "code")
	 */
	function submitLogin(data) {
		if (!data) return;

		setBusy(true);

		data = {
			...data,
			platform: isAndroid ? "android" : "web",
		};

		fetcher("/api/transaction", {
			body: data,
			timeout: 60000,
		})
			.then((responseData) => {
				console.log("[useLogin] LOGIN RESPONSE >>>> ", {
					responseData,
					data,
				});
				console.log("LOGIN RESPONSE >>>> ", { data, responseData });

				// ✅ STEP 1: OTP VERIFIED → CALL USER PROFILE API
				if (responseData?.response_type_id === 1541) {
					console.log("OTP VERIFIED → FETCH PROFILE");

					return fetcher("/api/transaction", {
						// The profile belongs to the session just opened; send its token (FE-6).
						token: responseData?.data?.otp_verification_token,
						body: {
							interaction_type_id: "151",
							bc: "26",
							mobile: data.mobile,
							source: "WLC",
							version: "v2",
							platform: data.platform,
							client_ref_id: Date.now().toString(),
							user_code: "99029899",
							user_identity_type: "mobile_number",
							initiator_id: "1234567891",
							org_id: data.org_id,
							org_token: data.org_token,
							user_identity: data.mobile,
							id_type: "Mobile",
							lang: "null",
							email: "",
							realsourceip: "",
						},
					})
						.then((profileResponse) => {
							console.log(
								"USER PROFILE RESPONSE:",
								profileResponse
							); // ✅ resolved data

							if (profileResponse?.response_type_id === 369) {
								const finalLoginResponse = {
									access_token:
										responseData?.data
											?.otp_verification_token,
									// Keep the session's refresh token and lifetime so the
									// token can be renewed before it expires (FE-3).
									refresh_token:
										responseData?.data?.refresh_token,
									access_token_lite:
										responseData?.data?.access_token,
									token_expiration:
										responseData?.data?.token_expiration,
									details: profileResponse?.data?.user_detail,
									personalDetails:
										profileResponse?.data?.personal_detail,
									shopDetails:
										profileResponse?.data?.shop_detail,
									accountDetails:
										profileResponse?.data?.account_detail,
								};

								processLoginResponse(finalLoginResponse);
								return;
							}

							// Profile not found — new user with no account yet.
							// Use the OTP verification token to start onboarding.
							const otpToken =
								responseData?.data?.otp_verification_token;
							if (otpToken) {
								console.log(
									"[useLogin] New user (profile not found). Starting onboarding."
								);
								processLoginResponse({
									access_token: otpToken,
									refresh_token:
										responseData?.data?.refresh_token,
									token_expiration:
										responseData?.data?.token_expiration,
									details: {
										mobile: data.mobile,
										onboarding: 1,
										signup_mobile: data.mobile,
									},
								});
								return;
							}

							throw new Error("Failed to fetch user profile");
						})
						.catch((err) => {
							console.error("PROFILE FETCH ERROR:", err);
							toast({
								title: "Failed to fetch user profile",
								status: "error",
							});
							setStep("LOGIN");
						});
				} else if (
					!(
						(
							responseData &&
							responseData.details &&
							responseData.access_token
						) // &&
						// responseData.loggedIn === true
						// &&
						// responseData.details.code &&
						// responseData.details.mobile &&
						// responseData.details.mobile.toString().length > 6
					)
				) {
					console.log("[useLogin] access_token not found.");

					if (responseData.otpFailed) {
						console.log("[useLogin] Wrong OTP.");
						toast({
							title: "Wrong OTP. Please try again.",
							status: "error",
							duration: 5000,
						});
						return;
					}

					if (responseData.accountInactive) {
						console.log("[useLogin] Account is inactive.");
						toast({
							title: "Your account has been temporarily blocked.",
							description: "Please contact support.",
							status: "error",
							duration: null,
						});
						setStep("LOGIN");
						return;
					}

					// New user: OTP was verified correctly but no account exists yet.
					// response_type_id may be 873 (agent doesn't exist) or similar.
					// Use the OTP verification token from response data to start onboarding.
					const otpToken = responseData?.data?.otp_verification_token;
					if (otpToken && !responseData?.otpFailed) {
						console.log(
							"[useLogin] New user OTP verified. Starting onboarding."
						);
						processLoginResponse({
							access_token: otpToken,
							details: {
								mobile: data.mobile,
								onboarding: 1,
								signup_mobile: data.mobile,
							},
						});
						return;
					}

					console.log("[useLogin] Login Failed → Back to Login");

					// Login Failed
					console.log(
						"[useLogin] Verification Failed. Go Back to Login."
					);
					toast({
						title: "Login failed.",
						status: "error",
						duration: 5000,
					});
					setStep("LOGIN");
					return;
				}

				if (
					responseData?.details?.onboarding == 1 &&
					responseData?.details?.user_type === -1 &&
					data?.id_type === "Google"
				) {
					console.log(
						"[useLogin] Social Login: Need to verify mobile"
					);
					setStep("SOCIAL_VERIFY");
					setEmail(responseData.details.email);
					setCachedSocialResponse(responseData);
					return;
				}

				// Disable onboarding if Self-Onboarding is not allowed
				if (
					responseData?.details?.onboarding == 1 &&
					!responseData?.details?.code &&
					isSelfOnboardingDisabled
				) {
					console.log(
						"[useLogin] Self-Onboarding Disabled → User Not Found → Back to Login"
					);
					toast({
						title: "User not found!",
						status: "error",
						duration: 6000,
					});
					setStep("LOGIN");
					console.log(
						"[useLogin] Verification Failed. Self-Onboarding is disabled"
					);
					return;
				}

				// Social Signup → Verify mobile number before starting signup process
				// MARK: New Gmail
				if (
					responseData?.details?.user_type === -1 &&
					data?.id_type === "Google" &&
					responseData?.details?.email
				) {
					console.log(
						"[useLogin] Google Login → Social Verify (mobile number verification)."
					);
					setStep("SOCIAL_VERIFY");
					setEmail(responseData.details.email);
					setCachedSocialResponse(responseData);
					return;
				}

				// Login Success (or, signup of new user)
				// MARK: Success
				console.log(
					"[useLogin] Login Successful → Processing Login Response (dispatch `LOGIN` to UserReducer)"
				);
				processLoginResponse(responseData);
			})
			.catch((e) => {
				console.error("[useLogin] Login Error:", e);
				// TODO: Show toast message with server error (<Err object>.body.message - parse `body` from string to JSON)
				toast({
					title: "Login failed. Try again or contact support.",
					status: "error",
					duration: 5000,
				});
				setStep("LOGIN");
			})
			.finally(() => setBusy(false));
	}

	return [busy, submitLogin];
}

export default useLogin;
