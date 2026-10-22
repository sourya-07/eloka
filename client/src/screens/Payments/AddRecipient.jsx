import {
	Box,
	Button,
	Divider,
	Flex,
	FormControl,
	FormLabel,
	Input,
	InputGroup,
	InputRightElement,
	Text,
	useToast,
} from "@chakra-ui/react";
import { useUser } from "@/contexts";
import { fetcher } from "@/lib/apiHelper";
import useRefreshToken from "@/hooks/useRefreshToken";
import { useRef, useState } from "react";

const INTERACTION_ADD_RECIPIENT = "789";

/**
 *
 * @param bank
 * @param name
 */
function getDepParam(bank, name) {
	return bank?.dependent_params?.find((p) => p.name === name);
}

const SectionLabel = ({ children }) => (
	<Text
		fontSize="xxs"
		fontWeight="semibold"
		color="gray.400"
		textTransform="uppercase"
		letterSpacing="widest"
		mb={3}
	>
		{children}
	</Text>
);

/**
 * AddRecipient — form to add a new payment recipient.
 * Bank list is received as a prop (pre-fetched during entry flow).
 * @param root0
 * @param root0.customerId
 * @param root0.bankList
 * @param root0.onSuccess
 * @param root0.onBack
 */
const AddRecipient = ({ customerId, bankList = [], onSuccess, onBack }) => {
	const { userData, accessToken } = useUser();
	const userDetails = userData?.userDetails ?? {};
	const { generateNewToken } = useRefreshToken();
	const toast = useToast();

	const [form, setForm] = useState({
		recipientName: "",
		mobile: "",
		account: "",
		ifsc: "",
	});
	const [selectedBank, setSelectedBank] = useState(null);
	const [bankSearch, setBankSearch] = useState("");
	const [showBankDropdown, setShowBankDropdown] = useState(false);
	const [loading, setLoading] = useState(false);
	const bankInputRef = useRef(null);

	const filteredBanks = bankSearch
		? bankList.filter((b) =>
				b.label.toLowerCase().includes(bankSearch.toLowerCase())
			)
		: bankList;

	const handleChange = (e) => {
		const { name, value } = e.target;
		setForm((prev) => ({ ...prev, [name]: value }));
	};

	const handleSelectBank = (bank) => {
		setSelectedBank(bank);
		setBankSearch(bank.label);
		setShowBankDropdown(false);
	};

	const handleClearBank = () => {
		setSelectedBank(null);
		setBankSearch("");
		bankInputRef.current?.focus();
	};

	const validate = () => {
		if (!form.recipientName.trim()) return "Recipient name is required";
		if (!/^\d{10}$/.test(form.mobile))
			return "Mobile number must be exactly 10 digits";
		if (!selectedBank) return "Please select a bank";

		const acctParam = getDepParam(selectedBank, "account");
		const minLen = acctParam?.length_min ?? 1;
		const maxLen = acctParam?.length_max ?? 20;
		if (!/^\d+$/.test(form.account))
			return "Account number must be numeric";
		if (form.account.length < minLen || form.account.length > maxLen)
			return `Account number must be ${minLen === maxLen ? minLen : `${minLen}–${maxLen}`} digits`;

		const ifscRequired = getDepParam(selectedBank, "ifsc_required");
		if (ifscRequired?.value !== 0 && !form.ifsc.trim())
			return "IFSC code is required for this bank";

		return null;
	};

	const handleSubmit = async (e) => {
		e.preventDefault();
		const err = validate();
		if (err) {
			toast({ title: err, status: "error", duration: 4000 });
			return;
		}

		const bankIdParam = getDepParam(selectedBank, "bank_id");
		setLoading(true);

		const payload = {
			interaction_type_id: INTERACTION_ADD_RECIPIENT,
			bc: userDetails?.org_id || "1",
			source: "NEWCONNECT",
			locale: "en",
			version: "v2",
			user_code: userDetails?.code || "",
			initiator_id: userDetails?.mobile || "",
			org_id: userDetails?.org_id || "1",
			user_identity: 0,
			recipient_name: form.recipientName,
			recipient_mobile: form.mobile,
			bank_code: selectedBank?.value || "",
			bank_id: bankIdParam?.value?.toString() || "",
			account: form.account,
			ifsc: form.ifsc,
			lang: "null",
			latlong: "",
			realsourceip: "",
			customer_id: customerId || userDetails?.mobile || "",
		};

		try {
			const data = await fetcher(
				"/api/transaction",
				{ token: accessToken, body: payload, timeout: 30000 },
				generateNewToken
			);

			if (data?.response_type_id === 2099) {
				toast({
					title: "Recipient added successfully",
					status: "success",
					duration: 4000,
				});
				onSuccess?.();
			} else {
				toast({
					title:
						data?.message ||
						"Failed to add recipient. Please try again.",
					status: "error",
					duration: 5000,
				});
			}
		} catch (err) {
			console.error("[AddRecipient] error:", err);
			toast({
				title: "Network error. Please check your connection.",
				status: "error",
				duration: 4000,
			});
		} finally {
			setLoading(false);
		}
	};

	const isIfscOptional =
		selectedBank && getDepParam(selectedBank, "ifsc_required")?.value === 0;

	const acctParam = selectedBank
		? getDepParam(selectedBank, "account")
		: null;
	const acctHint = acctParam
		? acctParam.length_min === acctParam.length_max
			? `${acctParam.length_min} digits`
			: `${acctParam.length_min}–${acctParam.length_max} digits`
		: null;

	const inputStyles = {
		size: "lg",
		borderRadius: "xl",
		borderColor: "gray.200",
		_focus: {
			borderColor: "blue.400",
			boxShadow: "0 0 0 3px rgba(66, 153, 225, 0.15)",
		},
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
			<Box h={1.5} bgGradient="linear(to-r, blue.600, blue.400)" />

			<Box p={6}>
				{/* Header */}
				<Flex align="center" mb={7} gap={3}>
					{onBack && (
						<Button
							variant="ghost"
							size="sm"
							onClick={onBack}
							px={2}
							color="gray.500"
							fontWeight="normal"
							_hover={{ color: "gray.800", bg: "gray.100" }}
						>
							← Back
						</Button>
					)}
					<Box>
						<Text fontSize="lg" fontWeight="bold" color="gray.800">
							Add Recipient
						</Text>
						<Text fontSize="sm" color="gray.400" mt={0.5}>
							Vendor: {customerId || "—"}
						</Text>
					</Box>
				</Flex>

				<form onSubmit={handleSubmit}>
					<Flex direction="column" gap={4}>
						{/* ── Recipient Info ── */}
						<SectionLabel>Recipient Info</SectionLabel>

						<FormControl isRequired>
							<FormLabel fontSize="sm">Full Name</FormLabel>
							<Input
								name="recipientName"
								placeholder="Enter full name"
								value={form.recipientName}
								onChange={handleChange}
								{...inputStyles}
							/>
						</FormControl>

						<FormControl isRequired>
							<FormLabel fontSize="sm">Mobile Number</FormLabel>
							<Input
								name="mobile"
								placeholder="10-digit mobile number"
								value={form.mobile}
								onChange={handleChange}
								maxLength={10}
								inputMode="numeric"
								{...inputStyles}
							/>
						</FormControl>

						<Divider borderColor="gray.100" mt={1} mb={1} />

						{/* ── Bank Details ── */}
						<SectionLabel>Bank Details</SectionLabel>

						{/* Searchable Bank Dropdown */}
						<FormControl isRequired>
							<FormLabel fontSize="sm">
								Bank
								{selectedBank && (
									<Text
										as="span"
										fontSize="xs"
										color="green.600"
										ml={2}
										fontWeight="normal"
									>
										✓ Selected
									</Text>
								)}
							</FormLabel>
							<Box position="relative">
								<InputGroup size="lg">
									<Input
										ref={bankInputRef}
										placeholder={
											bankList.length === 0
												? "Loading banks..."
												: "Search bank name..."
										}
										value={bankSearch}
										onChange={(e) => {
											setBankSearch(e.target.value);
											setSelectedBank(null);
											setShowBankDropdown(true);
										}}
										onFocus={() =>
											setShowBankDropdown(true)
										}
										onBlur={() =>
											setTimeout(
												() =>
													setShowBankDropdown(false),
												200
											)
										}
										autoComplete="off"
										isDisabled={bankList.length === 0}
										borderRadius="xl"
										borderColor={
											selectedBank
												? "green.300"
												: "gray.200"
										}
										bg={selectedBank ? "green.50" : "white"}
										_focus={{
											borderColor: "blue.400",
											boxShadow:
												"0 0 0 3px rgba(66, 153, 225, 0.15)",
										}}
										pr={selectedBank ? 10 : 4}
									/>
									{selectedBank && (
										<InputRightElement>
											<Box
												as="button"
												type="button"
												onClick={handleClearBank}
												w={6}
												h={6}
												borderRadius="full"
												bg="gray.200"
												color="gray.600"
												fontSize="xs"
												fontWeight="bold"
												display="flex"
												alignItems="center"
												justifyContent="center"
												_hover={{ bg: "gray.300" }}
												mr={2}
											>
												×
											</Box>
										</InputRightElement>
									)}
								</InputGroup>

								{showBankDropdown &&
									filteredBanks.length > 0 && (
										<Box
											position="absolute"
											top="100%"
											left={0}
											right={0}
											zIndex={200}
											bg="white"
											border="1px solid"
											borderColor="gray.200"
											borderRadius="xl"
											boxShadow="lg"
											maxH="220px"
											overflowY="auto"
											mt={1}
										>
											{filteredBanks.map((bank) => (
												<Box
													key={bank.value}
													px={4}
													py={3}
													cursor="pointer"
													fontSize="sm"
													color="gray.700"
													_hover={{
														bg: "blue.50",
														color: "blue.700",
													}}
													onMouseDown={() =>
														handleSelectBank(bank)
													}
												>
													{bank.label}
												</Box>
											))}
										</Box>
									)}
							</Box>
						</FormControl>

						<FormControl isRequired>
							<FormLabel fontSize="sm">
								Account Number
								{acctHint && (
									<Text
										as="span"
										fontSize="xs"
										color="gray.400"
										ml={1.5}
										fontWeight="normal"
									>
										({acctHint})
									</Text>
								)}
							</FormLabel>
							<Input
								name="account"
								placeholder="Enter account number"
								value={form.account}
								onChange={handleChange}
								inputMode="numeric"
								{...inputStyles}
							/>
						</FormControl>

						<FormControl isRequired={!isIfscOptional}>
							<FormLabel fontSize="sm">
								IFSC Code
								{isIfscOptional && (
									<Text
										as="span"
										fontSize="xs"
										color="gray.400"
										ml={1.5}
										fontWeight="normal"
									>
										(Optional)
									</Text>
								)}
							</FormLabel>
							<Input
								name="ifsc"
								placeholder="e.g. SBIN0001234"
								value={form.ifsc}
								onChange={handleChange}
								textTransform="uppercase"
								{...inputStyles}
							/>
						</FormControl>

						<Button
							type="submit"
							colorScheme="blue"
							size="lg"
							borderRadius="full"
							h={12}
							fontWeight="semibold"
							mt={3}
							isLoading={loading}
							loadingText="Adding..."
						>
							Add Recipient
						</Button>
					</Flex>
				</form>
			</Box>
		</Box>
	);
};

export default AddRecipient;
