import {
	Box,
	Button,
	Flex,
	FormControl,
	FormLabel,
	Input,
	Select,
	Spinner,
	Text,
	useToast,
} from "@chakra-ui/react";
import { fetcher } from "@/lib/apiHelper";
import { useUser } from "@/contexts";
import { useState } from "react";

const PAYMENT_MODES = [
	{ value: "5", label: "NEFT" },
	{ value: "4", label: "RTGS" },
	{ value: "1", label: "IMPS" },
];

/**
 * Calls the payout API (interaction_type_id = 392).
 * Returns the parsed response or throws on network error.
 * @param {object} payload - The payout request payload
 * @param {string} accessToken - The user's access token
 */
async function triggerPayout(payload, accessToken) {
	console.log("Payout API payload:", payload);
	return fetcher("/api/transaction", {
		token: accessToken,
		body: payload,
		timeout: 60000,
	});
}

/**
 * Validates the payout form fields.
 * @param {object} form - The form state
 * @returns {string|null} Error message, or null if valid
 */
function validatePayoutForm(form) {
	if (!form.amount || parseFloat(form.amount) <= 0)
		return "Amount must be greater than 0";
	if (!form.initiator_id || form.initiator_id.toString().length < 10)
		return "Valid 10-digit mobile number is required";
	if (!form.ifsc || form.ifsc.trim().length === 0)
		return "IFSC code is required";
	if (!form.account || form.account.trim().length < 6)
		return "Valid account number is required";
	if (!form.recipient_name || form.recipient_name.trim().length === 0)
		return "Recipient name is required";
	return null;
}

/**
 * PayoutForm — custom form for payout transactions (interaction_type_id = 392).
 * Pre-fills user_code, initiator_id, and org_id from the logged-in user's profile.
 */
const PayoutForm = () => {
	const { userData, accessToken } = useUser();
	const userDetails = userData?.userDetails ?? {};
	const toast = useToast();

	const [form, setForm] = useState({
		amount: "",
		payment_mode: "5",
		user_code: userDetails?.code ?? "",
		initiator_id: userDetails?.mobile ?? "",
		org_id: userDetails?.org_id ?? "",
		service_code: "45",
		sender_name: "",
		recipient_name: "",
		ifsc: "",
		account: "",
	});
	const [loading, setLoading] = useState(false);
	const [result, setResult] = useState(null); // { success: bool, message: string }

	const handleChange = (e) => {
		const { name, value } = e.target;
		setForm((prev) => ({ ...prev, [name]: value }));
	};

	const handleSubmit = async (e) => {
		e.preventDefault();
		setResult(null);

		const validationError = validatePayoutForm(form);
		if (validationError) {
			toast({ title: validationError, status: "error", duration: 4000 });
			return;
		}

		setLoading(true);
		const payload = {
			interaction_type_id: "392",
			amount: form.amount,
			payment_mode: form.payment_mode,
			user_code: form.user_code,
			initiator_id: form.initiator_id,
			org_id: form.org_id,
			service_code: form.service_code || "45",
			sender_name: form.sender_name,
			recipient_name: form.recipient_name,
			ifsc: form.ifsc,
			account: form.account,
			source: "NEWCONNECT",
		};

		try {
			const data = await triggerPayout(payload, accessToken);
			if (data?.status === 0) {
				const successMsg =
					data?.message || "Payout initiated successfully!";
				setResult({ success: true, message: successMsg });
				toast({
					title: "Payout successful!",
					description: successMsg,
					status: "success",
					duration: 5000,
				});
			} else {
				const errMsg =
					data?.message || "Payout failed. Please try again.";
				setResult({ success: false, message: errMsg });
				toast({ title: errMsg, status: "error", duration: 5000 });
			}
		} catch (err) {
			console.error("[PayoutForm] API error:", err);
			const errMsg =
				"Network error. Please check your connection and try again.";
			setResult({ success: false, message: errMsg });
			toast({ title: errMsg, status: "error", duration: 5000 });
		} finally {
			setLoading(false);
		}
	};

	return (
		<Box
			maxW="560px"
			mx="auto"
			mt={8}
			p={6}
			border="1px solid"
			borderColor="gray.200"
			borderRadius="lg"
			bg="white"
			boxShadow="sm"
		>
			<Text fontSize="xl" fontWeight="bold" mb={6} color="gray.800">
				Payout Transfer
			</Text>

			{result && (
				<Box
					mb={5}
					p={3}
					borderRadius="md"
					bg={result.success ? "green.50" : "red.50"}
					border="1px solid"
					borderColor={result.success ? "green.200" : "red.200"}
				>
					<Text
						fontWeight="medium"
						color={result.success ? "green.700" : "red.700"}
					>
						{result.message}
					</Text>
				</Box>
			)}

			<form onSubmit={handleSubmit}>
				<Flex direction="column" gap={4}>
					<FormControl isRequired>
						<FormLabel fontSize="sm">Amount (₹)</FormLabel>
						<Input
							name="amount"
							type="number"
							min="1"
							step="0.01"
							placeholder="Enter amount"
							value={form.amount}
							onChange={handleChange}
						/>
					</FormControl>

					<FormControl isRequired>
						<FormLabel fontSize="sm">Payment Mode</FormLabel>
						<Select
							name="payment_mode"
							value={form.payment_mode}
							onChange={handleChange}
						>
							{PAYMENT_MODES.map((m) => (
								<option key={m.value} value={m.value}>
									{m.label}
								</option>
							))}
						</Select>
					</FormControl>

					<Flex gap={4}>
						<FormControl>
							<FormLabel fontSize="sm">User Code</FormLabel>
							<Input
								name="user_code"
								placeholder="User code"
								value={form.user_code}
								onChange={handleChange}
							/>
						</FormControl>

						<FormControl isRequired>
							<FormLabel fontSize="sm">
								Initiator ID (Mobile)
							</FormLabel>
							<Input
								name="initiator_id"
								placeholder="10-digit mobile"
								value={form.initiator_id}
								onChange={handleChange}
								maxLength={10}
							/>
						</FormControl>
					</Flex>

					<Flex gap={4}>
						<FormControl>
							<FormLabel fontSize="sm">Org ID</FormLabel>
							<Input
								name="org_id"
								placeholder="Org ID"
								value={form.org_id}
								onChange={handleChange}
							/>
						</FormControl>

						<FormControl>
							<FormLabel fontSize="sm">Service Code</FormLabel>
							<Input
								name="service_code"
								placeholder="e.g. 45"
								value={form.service_code}
								onChange={handleChange}
							/>
						</FormControl>
					</Flex>

					<Flex gap={4}>
						<FormControl>
							<FormLabel fontSize="sm">Sender Name</FormLabel>
							<Input
								name="sender_name"
								placeholder="Sender name"
								value={form.sender_name}
								onChange={handleChange}
							/>
						</FormControl>

						<FormControl isRequired>
							<FormLabel fontSize="sm">Recipient Name</FormLabel>
							<Input
								name="recipient_name"
								placeholder="Recipient name"
								value={form.recipient_name}
								onChange={handleChange}
							/>
						</FormControl>
					</Flex>

					<Flex gap={4}>
						<FormControl isRequired>
							<FormLabel fontSize="sm">IFSC Code</FormLabel>
							<Input
								name="ifsc"
								placeholder="e.g. SBIN0001234"
								value={form.ifsc}
								onChange={handleChange}
								textTransform="uppercase"
							/>
						</FormControl>

						<FormControl isRequired>
							<FormLabel fontSize="sm">Account Number</FormLabel>
							<Input
								name="account"
								placeholder="Account number"
								value={form.account}
								onChange={handleChange}
							/>
						</FormControl>
					</Flex>

					<Button
						type="submit"
						colorScheme="blue"
						mt={2}
						isLoading={loading}
						loadingText="Processing..."
						leftIcon={loading ? <Spinner size="xs" /> : undefined}
					>
						Submit Payout
					</Button>
				</Flex>
			</form>
		</Box>
	);
};

export default PayoutForm;
