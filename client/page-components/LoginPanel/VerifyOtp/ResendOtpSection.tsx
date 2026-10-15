import { Flex, Text } from "@chakra-ui/react";
import { useEffect, useState } from "react";

interface ResendOtpSectionProps {
	/**
	 * Initial time in seconds for the countdown
	 */
	countdownSeconds: number;
	/**
	 * Function to call when resend OTP clicked. Must return a promise resolving to boolean indicating success.
	 */
	onResendOtp: () => Promise<boolean>;
}

/**
 * A section to resend OTP after a countdown timer expires
 * @param {ResendOtpSectionProps} props - Properties passed to the component
 * @returns {JSX.Element} The ResendOtpSection component
 */
export const ResendOtpSection: React.FC<ResendOtpSectionProps> = ({
	countdownSeconds,
	onResendOtp,
}) => {
	const [timer, setTimer] = useState(countdownSeconds);

	useEffect(() => {
		if (timer < 1) return undefined;

		const timeoutId = setTimeout(() => {
			setTimer((currTimer) => currTimer - 1);
		}, 1000);

		return () => clearTimeout(timeoutId);
	}, [timer]);

	return (
		<Flex
			justify="center"
			align="center"
			mt={{ base: 6, "2xl": "2.5rem" }}
			gap={1.5}
			userSelect="none"
		>
			{timer >= 1 ? (
				<>
					<Text fontSize="14px" color="#6B6B6B">
						Resend in
					</Text>
					<Text
						fontSize="14px"
						fontWeight="500"
						color="#0A0A0A"
						sx={{ fontVariantNumeric: "tabular-nums" }}
					>
						00:{timer <= 9 ? `0${timer}` : timer}
					</Text>
				</>
			) : (
				<>
					<Text fontSize="14px" color="#6B6B6B">
						Didn&apos;t receive it?
					</Text>
					<Text
						as="span"
						fontSize="14px"
						fontWeight="500"
						color="#0A0A0A"
						cursor="pointer"
						textDecoration="underline"
						textUnderlineOffset="2px"
						onClick={async () => {
							const didSend = await onResendOtp();
							if (didSend) setTimer(countdownSeconds);
						}}
					>
						Resend
					</Text>
				</>
			)}
		</Flex>
	);
};
