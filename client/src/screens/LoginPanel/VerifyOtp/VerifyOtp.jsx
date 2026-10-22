import { Box, Flex, Heading, Text, useToast } from "@chakra-ui/react";
import { Button, IcoButton, Icon, OtpInput } from "@/components/ui";
import { useAppSource, useOrgDetailContext } from "@/contexts";
import { useUser } from "@/contexts/UserContext";
import { fetcher, sendOtpRequest } from "@/lib";
import { useLogin } from "@/hooks";
import { useState } from "react";
import { ResendOtpSection } from "./ResendOtpSection";

const RESEND_OTP_COUNTDOWN_SECONDS = 30;

/**
 * A <VerifyOtp> component
 * @param {object} props - Properties passed to the component
 * @param {string} [props.loginType] - Login type, eg: Mobile
 * @param {string} [props.email] - Email of the user (used for social login OTP verification)
 * @param {object} props.number - Object containing the original and formatted mobile number
 * @param {object} props.cachedSocialResponse - Cached social login response. When OTP verification is successful, the cached response can be used to partially login the user (prep for onboarding)
 * @param {boolean} props.previewMode - Flag to check if the component is in preview mode
 * @param {Function} props.setStep - Function to set the step
 * @returns {JSX.Element} The VerifyOtp component
 */
const VerifyOtp = ({
	loginType,
	email,
	number,
	cachedSocialResponse,
	previewMode,
	setStep,
}) => {
	const { login } = useUser();
	const [loading, submitLogin] = useLogin(login, setStep);
	const toast = useToast();
	const { isAndroid } = useAppSource();
	const { orgDetail } = useOrgDetailContext();
	const { metadata } = orgDetail ?? {};
	const { login_meta } = metadata ?? {};
	const isMobileMappedUserId = login_meta?.mobile_mapped_user_id === 1;

	const [Otp, setOtp] = useState("");

	const resendOtpHandler = async () => {
		if (previewMode === true) return false;
		const { otp_sent } = await sendOtpRequest(
			orgDetail.org_id,
			number.original,
			toast,
			"resend",
			isAndroid,
			isMobileMappedUserId,
			orgDetail.org_token
		);
		if (!otp_sent) {
			// OTP failed..back to previous screen
			setStep(loginType === "Mobile" ? "LOGIN" : "SOCIAL_VERIFY");
			return false;
		}

		return true;
	};

	// For social login flow, we reach this screen only for new users (email not mapped).
	// In that case, we only verify the OTP (by calling API with interaction_type_id=194) and do not proceed with login in this step. Instead, we set the verified number and move to onboarding steps.
	// TODO: Need to handle the case where mobile is already registered but the email is being mapped for the first time. In that case, we should login the user instead of moving to onboarding. Or, allow users to map Social IDs (like, Gmail) only on their profile page.
	// MARK: SocialVerifyOTP
	const verifyOtpForSocialLogin = async (otp, mobile) => {
		if (previewMode === true) return;
		if (loading) return;
		if (!otp) return;
		if (!email || !cachedSocialResponse) return;

		const accessToken = cachedSocialResponse.access_token;

		try {
			const response = await fetcher("/api/transaction", {
				body: {
					interaction_type_id: "518",
					bc: "26",
					mobile: mobile,
					otp: otp,
					verification_type: "2",
					source: "WLC",
					version: "v2",
					client_ref_id: Date.now().toString(),
					user_code: "99029899",
					initiator_id: "1234567891",
					user_identity_type: "mobile_number",
					org_id: "26",
					user_identity: mobile,
					service_code: "49",
					lang: "null",
					intent_id: "0",
					realsourceip: "106.192.197.190",
				},
				token: accessToken,
			});

			if (response?.data) {
				// Extract response_type_id from the root level of response
				const responseTypeId = response.response_type_id;

				console.log("[VerifyOtp] After Google Login: API Response:", {
					responseTypeId,
					fullResponse: response,
				});

				if (
					responseTypeId === 874 // ??? Agent completed onboarding
				) {
					// TODO: Handle this case with better UX:
					// If another email is registered for this user, then show an error message. User must change email only after a normal login from his profile page.
					// If no email is registered, ask the user to connect this email to his existing profile. Also send an SMS to the user to avoid fraudulent account creation with random emails.
					// For now, we will just show a toast message and stay on the same screen.
					toast({
						title: "Mobile number already registered.",
						description:
							"Please go back and login with your mobile number and OTP.",
						status: "warning",
						duration: 5000,
						position: "top-right",
					});
					return;
				}

				if (
					responseTypeId === 876 || // OTP Verified Successfully
					responseTypeId === 873 || // ???? Agent doesn't exist, needs OTP verification
					responseTypeId === 862 // Agent exists, OTP verified but onboarding not complete
				) {
					// Login the user with the pre-cached social login response (which has the access token and other details) and set the verified mobile number in the user context (for use in onboarding steps)
					login(cachedSocialResponse, mobile);
					return;
				}
			}

			// Any other failure scenario...
			toast({
				title:
					response.message ||
					`Unknown error (#${response?.response_type_id}). Please try again.`,
				status: "error",
				duration: 5000,
				position: "top-right",
			});
		} catch (error) {
			console.error("Error checking agent:", error);
			toast({
				title:
					error?.message || "Something went wrong. Please try again.",
				status: "error",
				duration: 5000,
				position: "top-right",
			});
		}
	};

	const verifyOtpHandler = (_otp) => {
		if (previewMode === true) return;
		if (loading) return;
		if (!(_otp || Otp)) return;

		const _mobile = number?.verified ?? number?.original;

		// If `email` is set, and there is a previously cached social-login response, it means it's a Social-Login flow, else it's a mobile login flow
		if (email && cachedSocialResponse) {
			verifyOtpForSocialLogin(_otp || Otp, _mobile);
			return;
		}

		// Mobile login flow - directly submit login with OTP verification
		submitLogin({
			id_type: "Mobile",
			mobile: _mobile,
			id_token: _otp || Otp,
			org_id: orgDetail.org_id,
			org_token: orgDetail.org_token,
			interaction_type_id: "518",
			bc: "26",
			verification_type: "2",
			source: "WLC",
			version: "v2",
			client_ref_id: Date.now().toString(),
			user_code: "99029899",
			initiator_id: "1234567891",
			user_identity_type: "mobile_number",
			org_id: "26",
			user_identity: _mobile,
			service_code: "49",
			lang: "null",
			intent_id: "0",
			realsourceip: "106.192.197.190",
			otp: _otp || Otp,
			// ...(isMobileMappedUserId && { is_mobile_mapped_user_id: true }),
		});
	};

	const otpLength = number?.otpLength || 4;
	const isOtpComplete = Otp.length === otpLength;

	return (
		<Flex direction="column">
			{/* Back button */}
			<Box
				display="inline-flex"
				alignItems="center"
				gap={1.5}
				onClick={() =>
					setStep(loginType === "Mobile" ? "LOGIN" : "SOCIAL_VERIFY")
				}
				cursor="pointer"
				mb={8}
				w="fit-content"
				color="#6B6B6B"
				_hover={{ color: "#0A0A0A" }}
				transition="color 0.15s ease"
				userSelect="none"
			>
				<Icon name="arrow-back" size="18px" />
				<Text fontSize="14px" fontWeight="400">
					Back
				</Text>
			</Box>

			{/* Heading */}
			<Box mb={8}>
				<Heading
					as="h1"
					fontSize={{ base: "28px", "2xl": "32px" }}
					fontWeight="500"
					color="#0A0A0A"
					lineHeight="1.15"
					letterSpacing="-0.5px"
					whiteSpace="pre-line"
					userSelect="none"
				>
					{"Verify\nyour OTP"}
				</Heading>
				{/* Mobile number subtext — hidden for user-id login type */}
				{isMobileMappedUserId ? null : (
					<Flex align="center" mt={2} gap={1} userSelect="none">
						<Text fontSize="14px" color="#6B6B6B" lineHeight="1.4">
							Sent to +91 {number.formatted}
						</Text>
						<IcoButton
							iconName="mode-edit"
							size="sm"
							theme="primary"
							onClick={() =>
								setStep(
									loginType === "Mobile"
										? "LOGIN"
										: "SOCIAL_VERIFY"
								)
							}
						/>
					</Flex>
				)}
			</Box>

			{/* OTP input boxes — length driven by sendOtp response (3 for new users, 4 for existing) */}
			<Flex w="full" align="center" justify="center">
				<OtpInput
					inputStyle={{
						w: { base: "60px", sm: "64px" },
						h: { base: "68px", sm: "72px" },
						fontSize: { base: "22px", sm: "24px" },
						fontWeight: "600",
						borderRadius: "12px",
						borderColor: "#E5E5E0",
						background: "white",
						boxShadow: "none",
						sx: { fontVariantNumeric: "tabular-nums" },
						_focus: {
							borderColor: "#0A0A0A",
							boxShadow: "none",
							background: "white",
						},
					}}
					containerStyle={{
						columnGap: { base: "10px", sm: "14px" },
					}}
					length={otpLength}
					value={Otp}
					onChange={setOtp}
					onEnter={() => verifyOtpHandler()}
					onComplete={(otp) => {
						verifyOtpHandler(otp);
					}}
				/>
			</Flex>

			<ResendOtpSection
				countdownSeconds={RESEND_OTP_COUNTDOWN_SECONDS}
				onResendOtp={resendOtpHandler}
			/>

			{/* Verify button */}
			<Button
				variant="unstyled"
				w="full"
				h="54px"
				display="flex"
				alignItems="center"
				justifyContent="center"
				mt={{ base: 10, "2xl": "6.25rem" }}
				bg={isOtpComplete ? "#0A0A0A" : "#E5E5E0"}
				color={isOtpComplete ? "white" : "#A0A09A"}
				borderRadius="14px"
				fontSize="15px"
				fontWeight="500"
				letterSpacing="0.2px"
				loading={loading}
				disabled={loading || !isOtpComplete}
				_disabled={{ opacity: 1 }}
				onClick={() => verifyOtpHandler()}
				transition="background 0.15s ease, color 0.15s ease"
				_hover={isOtpComplete && !loading ? { opacity: 0.88 } : {}}
				_active={isOtpComplete && !loading ? { opacity: 0.75 } : {}}
			>
				Verify →
			</Button>
		</Flex>
	);
};

export default VerifyOtp;
