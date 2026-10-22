import { Box, Button, Flex, Input, Text, useToast } from "@chakra-ui/react";
import { useUser } from "@/contexts";
import { fetcher } from "@/lib/apiHelper";
import useRefreshToken from "@/hooks/useRefreshToken";
import { useRouter } from "next/router";
import { useRef, useState } from "react";
import { avatarColor, formatInr, initials, maskAccount } from "./utils";

const TXN_RESULT_STORAGE_KEY = "payment_txn_result";

const INTERACTION_SEND_MONEY = "392";

const PAYMENT_MODES = [
	{ value: "5", label: "IMPS", note: "Instant" },
	{ value: "4", label: "NEFT", note: "Batch" },
];

const RecipientBadge = ({ recipient }) => {
	const { text, bg } = avatarColor(recipient?.name || "");
	return (
		<Flex
			align="center"
			gap={3.5}
			p={4}
			bg="gray.50"
			borderRadius="2xl"
			border="1px solid"
			borderColor="gray.200"
		>
			<Flex
				w={11}
				h={11}
				borderRadius="full"
				bg={bg}
				align="center"
				justify="center"
				flexShrink={0}
			>
				<Text
					fontSize="md"
					fontWeight="bold"
					color={text}
					lineHeight="1"
				>
					{initials(recipient?.name || "")}
				</Text>
			</Flex>
			<Box flex={1} minW={0}>
				<Text fontWeight="semibold" color="gray.800" noOfLines={1}>
					{recipient?.name}
				</Text>
				<Text fontSize="xs" color="gray.500" mt={0.5} noOfLines={1}>
					{recipient?.bank}
				</Text>
				<Text
					fontSize="xs"
					color="gray.400"
					fontFamily="mono"
					letterSpacing="wider"
					mt={0.5}
				>
					{maskAccount(recipient?.account)}
					{recipient?.ifsc && (
						<Text as="span" ml={2} color="gray.300">
							· {recipient.ifsc}
						</Text>
					)}
				</Text>
			</Box>
		</Flex>
	);
};

/**
 * SendMoney — final step in the payments flow.
 * Large centered ₹ input + IMPS/NEFT pill toggle.
 * @param root0
 * @param root0.customerId
 * @param root0.recipient
 * @param root0.onBack
 */
const SendMoney = ({ customerId, recipient, onBack }) => {
	const { userData, accessToken } = useUser();
	const userDetails = userData?.userDetails ?? {};
	const { generateNewToken } = useRefreshToken();
	const toast = useToast();
	const router = useRouter();
	const amountRef = useRef(null);

	const [amount, setAmount] = useState("");
	const [paymentMode, setPaymentMode] = useState("5");
	const [loading, setLoading] = useState(false);

	const parsedAmount = parseFloat(amount);
	const isValidAmount = amount && parsedAmount > 0;

	const navigateToResult = (apiResponse) => {
		const modeLabel = PAYMENT_MODES.find(
			(m) => m.value === paymentMode
		)?.label;
		const meta = {
			amount,
			paymentMode,
			paymentModeLabel: modeLabel,
			senderName: userDetails?.name,
			recipient: {
				name: recipient?.name,
				account: recipient?.account,
				ifsc: recipient?.ifsc,
			},
		};
		try {
			sessionStorage.setItem(
				TXN_RESULT_STORAGE_KEY,
				JSON.stringify({ apiResponse, meta })
			);
		} catch {
			// sessionStorage unavailable — navigate without stored data
		}
		// Stay within admin routes if currently in the admin context
		const resultPath = router.pathname.startsWith("/admin/")
			? "/admin/payments/transaction-result"
			: "/payments/transaction-result";
		router.push(resultPath);
	};

	const handleSubmit = async () => {
		if (!isValidAmount) {
			toast({
				title: "Enter a valid amount",
				status: "error",
				duration: 4000,
			});
			return;
		}

		setLoading(true);
		const payload = {
			interaction_type_id: INTERACTION_SEND_MONEY,
			bc: userDetails?.org_id || "1",
			sender_name: userDetails?.name || "",
			source: "NEWCONNECT",
			locale: "en",
			version: "v2",
			user_code: userDetails?.code || "",
			initiator_id: userDetails?.mobile || "",
			org_id: userDetails?.org_id || "1",
			customer_id: customerId || userDetails?.mobile || "",
			pintwin: "1010",
			service_code: "45",
			ifsc: recipient?.ifsc || "",
			lang: "null",
			authentication: "neobank",
			recipient_id: recipient?.recipient_id || "",
			bank_code: recipient?.bank_code || "",
			payment_mode: paymentMode,
			amount: amount.toString(),
			is_consent: "1",
			latlong: "",
			recipient_id_type: "mobile_number",
			recipient_type: "2",
			role_for_retailer: "",
			recipient_name: recipient?.name || "",
			account: recipient?.account || "",
			realsourceip: "",
			skipPinVerification: "1",
		};

		try {
			const data = await fetcher(
				"/api/transaction",
				{ token: accessToken, body: payload, timeout: 60000 },
				generateNewToken
			);

			navigateToResult(data);
		} catch {
			toast({
				title: "Network error. Please check your connection.",
				status: "error",
				duration: 5000,
			});
		} finally {
			setLoading(false);
		}
	};

	return (
		<Flex direction="column" gap={4}>
			{/* Back nav */}
			<Flex align="center" gap={2} mb={1}>
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
				<Text fontSize="lg" fontWeight="bold" color="gray.800">
					Send Money
				</Text>
			</Flex>

			{/* Recipient badge */}
			<RecipientBadge recipient={recipient} />

			{/* Amount input panel */}
			<Box
				bg="white"
				border="1px solid"
				borderColor={isValidAmount ? "blue.200" : "gray.200"}
				borderRadius="2xl"
				py={8}
				px={6}
				textAlign="center"
				cursor="text"
				boxShadow={
					isValidAmount ? "0 0 0 3px rgba(66, 153, 225, 0.1)" : "none"
				}
				transition="all 0.2s"
				onClick={() => amountRef.current?.focus()}
			>
				<Text
					fontSize="xxs"
					fontWeight="semibold"
					color="gray.400"
					letterSpacing="widest"
					textTransform="uppercase"
					mb={4}
				>
					Enter Amount
				</Text>
				<Flex align="center" justify="center" gap={2}>
					<Text
						fontSize="3xl"
						fontWeight="bold"
						color={isValidAmount ? "gray.500" : "gray.300"}
						lineHeight="1"
						transition="color 0.15s"
					>
						₹
					</Text>
					<Input
						ref={amountRef}
						variant="unstyled"
						type="number"
						min="1"
						placeholder="0"
						value={amount}
						onChange={(e) => setAmount(e.target.value)}
						fontSize="5xl"
						fontWeight="extrabold"
						textAlign="center"
						color={isValidAmount ? "gray.800" : "gray.300"}
						_placeholder={{ color: "gray.200" }}
						w="full"
						maxW="240px"
						inputMode="decimal"
					/>
				</Flex>
				{isValidAmount && (
					<Text fontSize="sm" color="gray.400" mt={3}>
						₹ {formatInr(amount)}
					</Text>
				)}
			</Box>

			{/* Payment mode toggle */}
			<Box>
				<Text
					fontSize="xxs"
					fontWeight="semibold"
					color="gray.400"
					letterSpacing="widest"
					textTransform="uppercase"
					mb={2}
				>
					Payment Mode
				</Text>
				<Flex bg="gray.100" p="4px" borderRadius="full" gap={1}>
					{PAYMENT_MODES.map((m) => (
						<Button
							key={m.value}
							flex={1}
							size="sm"
							h={10}
							borderRadius="full"
							bg={
								paymentMode === m.value
									? "white"
									: "transparent"
							}
							color={
								paymentMode === m.value
									? "blue.600"
									: "gray.500"
							}
							fontWeight={
								paymentMode === m.value ? "semibold" : "normal"
							}
							boxShadow={paymentMode === m.value ? "sm" : "none"}
							_hover={{
								bg:
									paymentMode === m.value
										? "white"
										: "gray.200",
							}}
							onClick={() => setPaymentMode(m.value)}
							transition="all 0.15s"
						>
							{m.label}
							<Text
								as="span"
								fontSize="xxs"
								ml={1.5}
								fontWeight="normal"
								color={
									paymentMode === m.value
										? "blue.400"
										: "gray.400"
								}
							>
								· {m.note}
							</Text>
						</Button>
					))}
				</Flex>
			</Box>

			{/* CTA */}
			<Button
				colorScheme="blue"
				size="lg"
				h={14}
				borderRadius="full"
				fontWeight="semibold"
				fontSize="md"
				isLoading={loading}
				loadingText="Processing..."
				isDisabled={!isValidAmount}
				onClick={handleSubmit}
				mt={1}
				boxShadow={isValidAmount ? "sh-button" : "none"}
				transition="all 0.15s"
			>
				{isValidAmount
					? `Send ₹${formatInr(amount)}`
					: "Enter Amount to Continue"}
			</Button>
		</Flex>
	);
};

export default SendMoney;
