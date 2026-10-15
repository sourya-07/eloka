import {
	Box,
	Button,
	Flex,
	FormControl,
	FormLabel,
	Input,
	Text,
	useToast,
} from "@chakra-ui/react";
import { useUser } from "contexts";
import { fetcher } from "helpers/apiHelper";
import useRefreshToken from "hooks/useRefreshToken";
import { useState } from "react";

const INTERACTION_SEARCH_VENDOR = "150";
const INTERACTION_ADD_VENDOR = "110";

/**
 * AddVendor — step 1 of the Payments flow.
 * 1. Enter mobile → search vendor (150)
 * 2a. Found      → call onVendorReady(mobile, false)
 * 2b. Not found  → show name field → add vendor (110) → call onVendorReady(mobile, true)
 */
const AddVendor = ({ onVendorReady }) => {
	const { userData, accessToken } = useUser();
	const userDetails = userData?.userDetails ?? {};
	const { generateNewToken } = useRefreshToken();
	const toast = useToast();

	const [mobile, setMobile] = useState("");
	const [vendorName, setVendorName] = useState("");
	const [step, setStep] = useState("enter-mobile"); // "enter-mobile" | "enter-name"
	const [loading, setLoading] = useState(false);

	const baseFields = () => ({
		bc: userDetails?.org_id || "1",
		source: "NEWCONNECT",
		locale: "en",
		version: "v2",
		user_code: userDetails?.code || "",
		initiator_id: userDetails?.mobile || "",
		org_id: userDetails?.org_id || "1",
		lang: "null",
		latlong: "",
		realsourceip: "",
	});

	const handleMobileProceed = async () => {
		if (!/^\d{10}$/.test(mobile)) {
			toast({
				title: "Mobile number must be exactly 10 digits",
				status: "error",
				duration: 4000,
			});
			return;
		}

		setLoading(true);
		try {
			const data = await fetcher(
				"/api/transaction",
				{
					token: accessToken,
					body: {
						...baseFields(),
						interaction_type_id: INTERACTION_SEARCH_VENDOR,
						additional_info: "1",
						customer_id: mobile,
					},
					timeout: 30000,
				},
				generateNewToken
			);

			if (data?.response_type_id === 309 || data?.data?.is_registered === 1) {
				onVendorReady(mobile, false);
			} else {
				setStep("enter-name");
			}
		} catch (err) {
			console.error("[AddVendor] search error:", err);
			toast({
				title: "Failed to search vendor. Please try again.",
				status: "error",
				duration: 4000,
			});
		} finally {
			setLoading(false);
		}
	};

	const handleAddVendor = async () => {
		if (!vendorName.trim()) {
			toast({
				title: "Vendor name is required",
				status: "error",
				duration: 4000,
			});
			return;
		}

		setLoading(true);
		try {
			const data = await fetcher(
				"/api/transaction",
				{
					token: accessToken,
					body: {
						...baseFields(),
						interaction_type_id: INTERACTION_ADD_VENDOR,
						is_ekyc_required: "",
						ekyc_enabled: "0",
						name: vendorName,
						customer_id: mobile,
					},
					timeout: 30000,
				},
				generateNewToken
			);

			if (data?.response_type_id === 300 || data?.status === 0) {
				toast({
					title: "Vendor registered successfully",
					status: "success",
					duration: 3000,
				});
				onVendorReady(mobile, true);
			} else {
				toast({
					title:
						data?.message || "Failed to add vendor. Please try again.",
					status: "error",
					duration: 5000,
				});
			}
		} catch (err) {
			console.error("[AddVendor] add error:", err);
			toast({
				title: "Network error. Please try again.",
				status: "error",
				duration: 4000,
			});
		} finally {
			setLoading(false);
		}
	};

	return (
		<Box
			bg="white"
			borderRadius="2xl"
			border="1px solid"
			borderColor="gray.200"
			boxShadow="basic"
			overflow="hidden"
		>
			{/* Card header stripe */}
			<Box
				h={1.5}
				bgGradient="linear(to-r, blue.600, blue.400)"
				borderTopRadius="2xl"
			/>

			<Box p={6}>
				{/* Title block */}
				<Box mb={7}>
					<Text
						fontSize="lg"
						fontWeight="bold"
						color="gray.800"
						lineHeight="short"
					>
						Add Vendor
					</Text>
					<Text fontSize="sm" color="gray.500" mt={1}>
						Enter the vendor&apos;s mobile number to continue
					</Text>
				</Box>

				<Flex direction="column" gap={5}>
					{/* Locked mobile pill (shown in enter-name step) */}
					{step === "enter-name" ? (
						<Box
							p={3.5}
							bg="blue.50"
							borderRadius="xl"
							border="1px solid"
							borderColor="blue.100"
						>
							<Text
								fontSize="xxs"
								fontWeight="semibold"
								color="blue.500"
								textTransform="uppercase"
								letterSpacing="wide"
								mb={1}
							>
								Mobile Number
							</Text>
							<Flex align="center" justify="space-between">
								<Text
									fontSize="md"
									fontWeight="bold"
									color="blue.800"
									letterSpacing="wider"
								>
									{mobile}
								</Text>
								<Flex
									w={5}
									h={5}
									borderRadius="full"
									bg="green.500"
									align="center"
									justify="center"
									flexShrink={0}
								>
									<Text
										fontSize="xxs"
										fontWeight="bold"
										color="white"
										lineHeight="1"
									>
										✓
									</Text>
								</Flex>
							</Flex>
						</Box>
					) : (
						<FormControl isRequired>
							<FormLabel fontSize="sm">Mobile Number</FormLabel>
							<Input
								placeholder="10-digit mobile number"
								value={mobile}
								onChange={(e) =>
									setMobile(
										e.target.value.replace(/\D/g, "").slice(0, 10)
									)
								}
								inputMode="numeric"
								size="lg"
								borderRadius="xl"
								borderColor="gray.200"
								_focus={{
									borderColor: "blue.400",
									boxShadow: "0 0 0 3px rgba(66, 153, 225, 0.15)",
								}}
								isDisabled={loading}
							/>
						</FormControl>
					)}

					{/* New vendor notice + name field */}
					{step === "enter-name" && (
						<>
							<Box
								p={4}
								bg="orange.50"
								borderRadius="xl"
								border="1px solid"
								borderColor="orange.200"
							>
								<Text
									fontSize="xxs"
									fontWeight="semibold"
									color="orange.600"
									textTransform="uppercase"
									letterSpacing="wide"
									mb={1.5}
								>
									New Vendor
								</Text>
								<Text fontSize="sm" color="gray.600" lineHeight="base">
									This number is not registered. Enter the vendor&apos;s
									name to create their account.
								</Text>
							</Box>

							<FormControl isRequired>
								<FormLabel fontSize="sm">Vendor Name</FormLabel>
								<Input
									placeholder="Enter full name"
									value={vendorName}
									onChange={(e) => setVendorName(e.target.value)}
									size="lg"
									borderRadius="xl"
									borderColor="gray.200"
									_focus={{
										borderColor: "blue.400",
										boxShadow: "0 0 0 3px rgba(66, 153, 225, 0.15)",
									}}
									autoFocus
								/>
							</FormControl>
						</>
					)}

					{/* CTA buttons */}
					{step === "enter-mobile" ? (
						<Button
							colorScheme="blue"
							size="lg"
							borderRadius="full"
							h={12}
							fontWeight="semibold"
							isLoading={loading}
							loadingText="Searching..."
							onClick={handleMobileProceed}
							mt={1}
						>
							Proceed
						</Button>
					) : (
						<Flex gap={3} mt={1}>
							<Button
								variant="outline"
								flex={1}
								size="lg"
								borderRadius="full"
								h={12}
								onClick={() => {
									setStep("enter-mobile");
									setVendorName("");
								}}
								isDisabled={loading}
							>
								Back
							</Button>
							<Button
								colorScheme="blue"
								flex={2}
								size="lg"
								borderRadius="full"
								h={12}
								fontWeight="semibold"
								isLoading={loading}
								loadingText="Registering..."
								onClick={handleAddVendor}
							>
								Register &amp; Proceed
							</Button>
						</Flex>
					)}
				</Flex>
			</Box>
		</Box>
	);
};

export default AddVendor;
