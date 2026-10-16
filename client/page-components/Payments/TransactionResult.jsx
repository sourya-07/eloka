import { Box, Button, Divider, Flex, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useState } from "react";
import { formatInr, maskAccount } from "./utils";

const STORAGE_KEY = "payment_txn_result";

const StatusIcon = ({ isSuccess, isPending }) => {
	const config = isSuccess
		? {
				bg: "green.50",
				border: "green.200",
				color: "green.500",
				symbol: "✓",
			}
		: isPending
			? {
					bg: "yellow.50",
					border: "yellow.200",
					color: "yellow.500",
					symbol: "⏳",
				}
			: {
					bg: "red.50",
					border: "red.200",
					color: "red.500",
					symbol: "✕",
				};

	return (
		<Box
			w={20}
			h={20}
			borderRadius="full"
			bg={config.bg}
			border="3px solid"
			borderColor={config.border}
			display="flex"
			alignItems="center"
			justifyContent="center"
			mx="auto"
			mb={5}
		>
			<Text
				fontSize="3xl"
				fontWeight="black"
				color={config.color}
				lineHeight="1"
			>
				{config.symbol}
			</Text>
		</Box>
	);
};

const DetailRow = ({ label, value, mono }) => {
	if (value === null || value === undefined || value === "") return null;
	return (
		<Flex justify="space-between" align="flex-start" py={3} gap={4}>
			<Text fontSize="sm" color="gray.500" flexShrink={0} minW="40%">
				{label}
			</Text>
			<Text
				fontSize="sm"
				fontWeight="medium"
				color="gray.800"
				textAlign="right"
				fontFamily={mono ? "mono" : undefined}
				letterSpacing={mono ? "wide" : undefined}
				wordBreak="break-all"
			>
				{value}
			</Text>
		</Flex>
	);
};

const SectionLabel = ({ label }) => (
	<Flex align="center" gap={3} my={2}>
		<Divider />
		<Text
			fontSize="xxs"
			fontWeight="semibold"
			color="gray.400"
			letterSpacing="widest"
			textTransform="uppercase"
			flexShrink={0}
		>
			{label}
		</Text>
		<Divider />
	</Flex>
);

const NoDataView = ({ onGoHome }) => (
	<Flex
		direction="column"
		align="center"
		justify="center"
		minH="55vh"
		px={4}
		gap={5}
	>
		<Box
			w={16}
			h={16}
			borderRadius="full"
			bg="gray.100"
			border="2px solid"
			borderColor="gray.200"
			display="flex"
			alignItems="center"
			justifyContent="center"
		>
			<Text fontSize="2xl" color="gray.400" lineHeight="1">
				?
			</Text>
		</Box>
		<Box textAlign="center">
			<Text fontWeight="bold" fontSize="lg" color="gray.700" mb={2}>
				Transaction Details Unavailable
			</Text>
			<Text fontSize="sm" color="gray.500" lineHeight="tall">
				No transaction data was found. This can happen if you navigated
				here directly or refreshed after leaving the payment flow.
			</Text>
		</Box>
		<Button
			colorScheme="blue"
			borderRadius="full"
			h={11}
			px={8}
			fontWeight="semibold"
			onClick={onGoHome}
		>
			Back to Home
		</Button>
	</Flex>
);

const readStoredTxData = () => {
	if (typeof window === "undefined") return null;
	try {
		const raw = sessionStorage.getItem(STORAGE_KEY);
		return raw ? JSON.parse(raw) : null;
	} catch {
		return null;
	}
};

const TransactionResult = () => {
	const router = useRouter();
	const [txData] = useState(readStoredTxData);
	const isAdmin = router.pathname.startsWith("/admin/");
	const prefix = isAdmin ? "/admin" : "";

	const clearAndNavigate = (path) => {
		try {
			sessionStorage.removeItem(STORAGE_KEY);
		} catch {
			// ignore
		}
		router.push(path);
	};

	if (!txData) {
		return <NoDataView onGoHome={() => router.push(`${prefix}/home`)} />;
	}

	const { apiResponse, meta } = txData;
	const txDetails = apiResponse?.data || {};
	const isSuccess = txDetails.tx_status === "0";
	const isPending = txDetails.tx_status === "1";

	const statusLabel = isSuccess
		? "Transfer Successful"
		: isPending
			? "Transfer Pending"
			: "Transfer Failed";

	const statusDesc =
		txDetails.txstatus_desc &&
		txDetails.txstatus_desc !== txDetails.tx_status
			? txDetails.txstatus_desc
			: apiResponse?.message ||
				(isSuccess
					? "Your money has been sent."
					: "Transaction could not be processed.");

	const totalDebited =
		txDetails.amount && (txDetails.fee || txDetails.gst)
			? (
					parseFloat(txDetails.amount || 0) +
					parseFloat(txDetails.fee || 0) +
					parseFloat(txDetails.gst || 0)
				).toFixed(2)
			: null;

	const statusBg = isSuccess
		? "green.50"
		: isPending
			? "yellow.50"
			: "red.50";
	const statusBorderColor = isSuccess
		? "green.200"
		: isPending
			? "yellow.200"
			: "red.200";
	const statusTextColor = isSuccess
		? "green.700"
		: isPending
			? "yellow.700"
			: "red.700";

	return (
		<Box maxW="520px" mx="auto" pt={6} px={{ base: 4, md: 0 }} pb={12}>
			{/* Hero status card */}
			<Box
				bg={statusBg}
				border="1px solid"
				borderColor={statusBorderColor}
				borderRadius="2xl"
				p={8}
				textAlign="center"
				mb={5}
			>
				<StatusIcon isSuccess={isSuccess} isPending={isPending} />
				<Text
					fontSize="xl"
					fontWeight="extrabold"
					color={statusTextColor}
					mb={2}
				>
					{statusLabel}
				</Text>
				{txDetails.amount && (
					<Text
						fontSize="4xl"
						fontWeight="black"
						color="gray.800"
						lineHeight="1.15"
						mb={2}
					>
						₹{formatInr(txDetails.amount)}
					</Text>
				)}
				<Text fontSize="sm" color="gray.500" lineHeight="tall">
					{statusDesc}
				</Text>
			</Box>

			{/* Details card */}
			<Box
				bg="white"
				border="1px solid"
				borderColor="gray.100"
				borderRadius="2xl"
				px={5}
				py={3}
				mb={5}
			>
				<SectionLabel label="Transfer" />
				<DetailRow
					label="Status"
					value={txDetails.txstatus_desc || statusLabel}
				/>
				<DetailRow
					label="Amount"
					value={
						txDetails.amount
							? `₹${formatInr(txDetails.amount)}`
							: null
					}
				/>
				<DetailRow
					label="Fee"
					value={
						txDetails.fee ? `₹${formatInr(txDetails.fee)}` : null
					}
				/>
				<DetailRow
					label="GST"
					value={
						txDetails.gst ? `₹${formatInr(txDetails.gst)}` : null
					}
				/>
				<DetailRow
					label="Total Debited"
					value={totalDebited ? `₹${formatInr(totalDebited)}` : null}
				/>
				{meta?.paymentModeLabel && (
					<DetailRow
						label="Payment Mode"
						value={meta.paymentModeLabel}
					/>
				)}

				<SectionLabel label="Parties" />
				<DetailRow
					label="From"
					value={txDetails.sender_name || meta?.senderName}
				/>
				<DetailRow
					label="To"
					value={txDetails.recipient_name || meta?.recipient?.name}
				/>
				<DetailRow
					label="Account"
					value={
						txDetails.account
							? maskAccount(txDetails.account)
							: null
					}
					mono
				/>
				<DetailRow
					label="IFSC"
					value={txDetails.ifsc || meta?.recipient?.ifsc}
					mono
				/>

				<SectionLabel label="Reference" />
				<DetailRow label="Transaction ID" value={txDetails.tid} mono />
				<DetailRow
					label="Client Ref ID"
					value={txDetails.client_ref_id}
					mono
				/>
				<DetailRow
					label="Bank Ref No."
					value={txDetails.bank_ref_num}
					mono
				/>
				{txDetails.timestamp ? (
					<DetailRow
						label="Date & Time"
						value={txDetails.timestamp}
					/>
				) : null}
			</Box>

			{/* Action buttons */}
			<Flex direction="column" gap={3}>
				<Button
					colorScheme="blue"
					size="lg"
					h={14}
					borderRadius="full"
					fontWeight="semibold"
					fontSize="md"
					onClick={() =>
						clearAndNavigate(`${prefix}/transaction/996`)
					}
				>
					New Transfer
				</Button>
				<Flex gap={3}>
					<Button
						variant="outline"
						size="lg"
						h={12}
						flex={1}
						borderRadius="full"
						fontWeight="semibold"
						fontSize="sm"
						borderColor="gray.200"
						color="gray.600"
						_hover={{ bg: "gray.50" }}
						onClick={() => clearAndNavigate(`${prefix}/home`)}
					>
						Back to Home
					</Button>
					<Button
						variant="outline"
						size="lg"
						h={12}
						flex={1}
						borderRadius="full"
						fontWeight="semibold"
						fontSize="sm"
						borderColor="gray.200"
						color="gray.600"
						_hover={{ bg: "gray.50" }}
						onClick={() => clearAndNavigate(`${prefix}/history`)}
					>
						View History
					</Button>
				</Flex>
			</Flex>
		</Box>
	);
};

export default TransactionResult;
