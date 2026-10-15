import {
	Box,
	Input as ChakraInput,
	Flex,
	Heading,
	InputGroup,
	InputLeftElement,
	Text,
	useToast,
} from "@chakra-ui/react";
import { Button, Icon, OrgLogo } from "components";
import { useAppSource, useOrgDetailContext } from "contexts";
import { sendOtpRequest } from "helpers";
import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
// import { GoogleButton } from "./GoogleButton";

const DynamicGoogleButton = dynamic(
	() => import("./GoogleButton/GoogleButton"),
	{
		ssr: false,
		// loading: () => <p>Loading...</p>,
	}
);

/**
 * A <Login> component
 * Responsible for handling different types of login like Mobile Number & Google Login
 * @param {object} props
 * @param {boolean} [props.hideLogo] - Flag to hide the logo
 * @param {Function} props.setStep - Function to set the step like LOGIN, VERIFYOTP & SOCIALVERIFY
 * @param {Function} props.setNumber - Function to set the users mobile number if the user is loogin using Mobile number
 * @param {object} props.number - Object which gives the number in two forms formatted and original (or unformatted)
 * @param {string} props.lastUserName - Last user name who logged in
 * @param {string} props.lastMobileFormatted - Last mobile number which was used to login
 * @param {boolean} props.previewMode - Flag to check if the component is in preview mode
 * @param {Function} props.setEmail - Function to set the users email
 * @param {Function} props.setLoginType - Function to set the login type
 * @param {Function} props.setCachedSocialResponse - Function to set the cached social login response (used for mobile verification in social login)
 */
const Login = ({
	hideLogo = false,
	setStep,
	setNumber,
	number,
	lastUserName,
	lastMobileFormatted,
	previewMode,
	setEmail,
	setLoginType,
	setCachedSocialResponse,
}) => {
	const EnterRef = useRef();
	const toast = useToast();
	const { isAndroid } = useAppSource();

	const { orgDetail } = useOrgDetailContext();
	const { metadata } = orgDetail ?? {};
	const { login_meta } = metadata ?? {};
	const isMobileMappedUserId = login_meta?.mobile_mapped_user_id === 1;
	const mobileMappedUserIdLabel = login_meta?.user_id_label || "User ID";
	const UserIdType = isMobileMappedUserId
		? mobileMappedUserIdLabel
		: "Mobile Number";

	const [value, setValue] = useState(number.original || "");
	const [errorMsg, setErrorMsg] = useState("");
	const [invalid, setInvalid] = useState(false);

	useEffect(() => {
		if (lastMobileFormatted && !value) {
			setValue(lastMobileFormatted?.replaceAll(" ", ""));
		}
		// WARNING: Do not add "value" as a dependency here (as it will always auto-fill the last number whenever the user deletes the filled number & therefore the value becomes empty)
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [lastMobileFormatted]);

	// Is Google Login available?
	const showGoogle =
		orgDetail?.login_types?.google?.default ||
		orgDetail?.login_types?.google?.client_id
			? true
			: false;

	// True when the field has a valid entry and the Continue button should be active
	const isFormValid = isMobileMappedUserId
		? value.length >= 5 && value.length <= 10
		: value.length === 10;

	const sendOtp = async () => {
		if (previewMode === true) return;

		// check based on the isMobileMappedUserId,
		// if isMobileMappedUserId is true, then allow digits 5 to 10
		// else exactly 10 digits are required
		if (
			(isMobileMappedUserId && value.length >= 5 && value.length <= 10) ||
			(!isMobileMappedUserId && value.length === 10)
		) {
			// Input component now returns unformatted value directly
			setLoginType("Mobile");
			setStep("VERIFY_OTP");

			const { otp_sent, verifiedMobileNumber, otpLength } = await sendOtpRequest(
				orgDetail.org_id,
				value,
				toast,
				"send",
				isAndroid,
				isMobileMappedUserId,
				orgDetail.org_token
			);

			// Format the value for display purposes
			const formattedValue = value.replace(
				/(\d{3})(\d{3})(\d{4})/,
				"$1 $2 $3"
			);

			setNumber({
				original: value,
				formatted: formattedValue,
				verified: verifiedMobileNumber,
				otpLength: otpLength,
			});

			if (otp_sent) {
				// Set login-type for current session...
				sessionStorage.setItem("login_type", "Mobile");
			} else {
				// OTP failed..back to previous screen
				setStep("LOGIN");
			}
		} else {
			setErrorMsg("Required");
			setInvalid(true);
		}
	};

	const onkeyHandler = (e) => {
		if (e.code === "Enter") {
			sendOtp();
		}
	};

	return (
		<Flex direction="column" h="100%">
			{/* Logo */}
			{hideLogo ? null : (
				<Box mb={8}>
					<OrgLogo size="md" />
				</Box>
			)}

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
					{lastUserName
						? `Welcome back,\n${lastUserName}`
						: "Welcome\nBack"}
				</Heading>
				<Text
					fontSize="14px"
					color="#6B6B6B"
					mt={2}
					lineHeight="1.4"
					userSelect="none"
				>
					Login to continue
				</Text>
			</Box>

			{/* Mobile number input */}
			<Box mb={5}>
				<InputGroup h="54px">
					{!isMobileMappedUserId && (
						<InputLeftElement
							pointerEvents="none"
							h="54px"
							w="62px"
						>
							<Flex
								h="100%"
								w="100%"
								align="center"
								justify="center"
								borderRight="1px solid #E5E5E0"
							>
								<Text
									fontSize="15px"
									color="#6B6B6B"
									fontWeight="400"
									userSelect="none"
								>
									+91
								</Text>
							</Flex>
						</InputLeftElement>
					)}
					<ChakraInput
						h="54px"
						pl={isMobileMappedUserId ? 4 : "70px"}
						pr={4}
						border="1px solid"
						borderColor={invalid ? "error" : "#E5E5E0"}
						borderRadius="12px"
						bg="white"
						fontSize="16px"
						fontWeight="400"
						color="#0A0A0A"
						placeholder={
							isMobileMappedUserId ? "" : "Mobile number"
						}
						_placeholder={{ color: "#ADADAD" }}
						inputMode="numeric"
						autoFocus
						maxLength={10}
						value={value}
						onChange={(e) => {
							const raw = e.target.value
								.replace(/\D/g, "")
								.slice(0, 10);
							setValue(raw);
							if (invalid) setInvalid(false);
						}}
						onFocus={() => setInvalid(false)}
						onKeyDown={onkeyHandler}
						_focus={{
							borderColor: "#0A0A0A",
							boxShadow: "none",
							bg: "white",
						}}
						_hover={{ borderColor: "#C5C5C0" }}
						focusBorderColor="#0A0A0A"
					/>
				</InputGroup>
				{invalid && errorMsg && (
					<Text fontSize="12px" color="error" mt={1.5} ml={1}>
						{errorMsg}
					</Text>
				)}
			</Box>

			{/* CTA button */}
			<Button
				variant="unstyled"
				w="full"
				h="54px"
				display="flex"
				alignItems="center"
				justifyContent="center"
				bg={isFormValid ? "#0A0A0A" : "#E5E5E0"}
				color={isFormValid ? "white" : "#A0A09A"}
				borderRadius="14px"
				fontSize="15px"
				fontWeight="500"
				letterSpacing="0.2px"
				onClick={sendOtp}
				ref={EnterRef}
				transition="background 0.15s ease, color 0.15s ease"
				_hover={isFormValid ? { opacity: 0.88 } : {}}
				_active={isFormValid ? { opacity: 0.75 } : {}}
			>
				Continue →
			</Button>

			{/* Trust indicator */}
			<Flex justify="center" align="center" gap={1.5} mt={4}>
				<Icon name="lock" size="12px" color="#ADADAD" />
				<Text
					fontSize="12px"
					color="#ADADAD"
					userSelect="none"
					letterSpacing="0.1px"
				>
					Secured by DemoPortal
				</Text>
			</Flex>

			{/* Google / social login */}
			{showGoogle ? (
				<>
					<Box flex="1 1 48px" minH="24px" />

					<Flex align="center" w="100%" gap={3}>
						<Box flex="1" h="1px" bg="#E5E5E0" />
						<Text
							fontSize="12px"
							color="#ADADAD"
							userSelect="none"
							flexShrink={0}
						>
							or continue with
						</Text>
						<Box flex="1" h="1px" bg="#E5E5E0" />
					</Flex>

					<Flex justify="center" mt={4}>
						<DynamicGoogleButton
							setStep={setStep}
							setLoginType={setLoginType}
							setNumber={setNumber}
							setEmail={setEmail}
							setCachedSocialResponse={setCachedSocialResponse}
						/>
					</Flex>
				</>
			) : null}

			<Box flex="1" />
		</Flex>
	);
};

export default Login;
