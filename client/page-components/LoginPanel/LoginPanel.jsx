import { Box, Flex, Text } from "@chakra-ui/react";
import { Icon } from "components";
import { useSession } from "contexts";
import { fadeIn } from "libs/chakraKeyframes";
import Link from "next/link";
import { useState } from "react";
import { LoginWidget } from "./LoginWidget";
import { useRestoreLastLoginOrRoute } from "./useRestoreLastLoginOrRoute";

/**
 * This is the main component where all the Login related components are rendered.
 * @param {*} props
 * @param {string} props.cmsType Type of CMS to render. (reserved for future use)
 * @param {object} props.cmsData Custom CMS data to render. (reserved for future use)
 */
const LoginPanel = ({ cmsType, cmsData }) => {
	const [number, setNumber] = useState({
		original: "",
		formatted: "",
	});
	const [showWelcomeCard, setShowWelcomeCard] = useState(false);
	const { isLoggedIn } = useSession();

	// Restore last login or OTP route from localStorage
	useRestoreLastLoginOrRoute({
		number,
		setNumber,
		setShowWelcomeCard,
	});

	// Hide login panel if user is already logged in
	if (isLoggedIn) return null;

	// MARK: JSX
	return (
		<Box
			boxSizing="border-box"
			w="full"
			h="100vh"
			maxH="100vh"
			bg="#FAFAF7"
			bgImage={{
				base: "none",
				lg: "url('/login_bg_2.opt.svg')",
			}}
			backgroundRepeat="no-repeat"
			backgroundPosition="center center"
			backgroundSize="cover"
			overflowY="auto"
		>
			<Flex
				direction="column"
				align="center"
				justify="center"
				minH="100%"
				p={{ base: 0, md: "20px 0" }}
			>
				{/* Login Widget — centered card, no drop-shadow */}
				<LoginWidget
					w={{ base: "100%", md: "440px" }}
					h={{ base: "100vh", md: "auto" }}
					minH={{ md: "520px" }}
					borderRadius={{ base: 0, md: "20px" }}
					boxShadow="none"
					border={{ base: "none", md: "1px solid #E5E5E0" }}
					px={{ base: 8, md: 10 }}
					py={{ base: 10, md: 10 }}
					animation={`${fadeIn} ease-out 0.3s`}
				/>
			</Flex>

			{/* Privacy Policy Link */}
			<Flex
				pos="absolute"
				bottom="6px"
				right="6px"
				fontWeight="medium"
				fontSize="xs"
				color={{ base: "dark", md: "white" }}
				opacity="0.4"
			>
				<Link href="/privacy" prefetch={false}>
					<Flex gap="1" align="center">
						<Text display="inline" lineHeight="1">
							Privacy Policy
						</Text>
						<Icon name="open-in-new" size="xs" />
					</Flex>
				</Link>
			</Flex>
		</Box>
	);
};

export default LoginPanel;
