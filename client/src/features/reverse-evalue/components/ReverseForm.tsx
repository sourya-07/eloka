import {
	Box,
	Button,
	FormControl,
	FormErrorMessage,
	FormLabel,
	Grid,
	GridItem,
	Input,
	Text,
} from "@chakra-ui/react";
import { useState } from "react";
import { ReversibleRecord, ReverseType } from "../context/types";

const fmt = (n: number) =>
	`₹ ${Number(n).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;

interface ReverseFormProps {
	record: ReversibleRecord;
	reverseType: ReverseType;
	isSubmitting: boolean;
	errorMessage: string | null;
	onSubmit: (_params: { amount: string; reason: string }) => void;
	onBack: () => void;
}

export const ReverseForm = ({
	record,
	reverseType,
	isSubmitting,
	errorMessage,
	onSubmit,
	onBack,
}: ReverseFormProps) => {
	const isPartial = reverseType === "partial";
	const [amount, setAmount] = useState(
		isPartial ? "" : String(record.reversible_amount)
	);
	const [reason, setReason] = useState("");
	const [touched, setTouched] = useState({
		amount: false,
		reason: false,
	});

	const amountNum = parseFloat(amount);
	const amountError =
		touched.amount && isPartial
			? !amount
				? "Amount is required"
				: isNaN(amountNum) || amountNum <= 0
					? "Enter a valid amount"
					: amountNum > record.reversible_amount
						? `Cannot exceed reversible amount ${fmt(record.reversible_amount)}`
						: ""
			: "";
	const reasonError =
		touched.reason && !reason.trim() ? "Reason is required" : "";

	const isValid =
		!amountError &&
		!reasonError &&
		(isPartial ? !!amount : true) &&
		reason.trim();

	const handleSubmit = () => {
		setTouched({ amount: true, reason: true });
		if (!isValid) return;
		onSubmit({ amount, reason });
	};

	return (
		<Box>
			<Text fontSize="lg" fontWeight="semibold" mb={4}>
				{isPartial ? "Reverse Partial Amount" : "Reverse Full Amount"}
			</Text>

			{/* Summary card */}
			<Box
				mb={5}
				p={4}
				bg="orange.50"
				borderRadius="md"
				border="1px solid"
				borderColor="orange.200"
			>
				<Grid templateColumns="repeat(2, 1fr)" gap={3}>
					<GridItem>
						<Text fontSize="xs" color="gray.500">
							Agent&apos;s User Code
						</Text>
						<Text fontSize="sm" fontWeight="medium">
							{record.csp_code}
						</Text>
					</GridItem>
					<GridItem>
						<Text fontSize="xs" color="gray.500">
							E-value Transferred
						</Text>
						<Text fontSize="sm" fontWeight="medium">
							{fmt(record.value_transferred)}
						</Text>
					</GridItem>
					<GridItem>
						<Text fontSize="xs" color="gray.500">
							Reversed Amount
						</Text>
						<Text fontSize="sm" fontWeight="medium">
							{fmt(record.reverted_amount)}
						</Text>
					</GridItem>
					<GridItem>
						<Text fontSize="xs" color="gray.500">
							Reversible Amount
						</Text>
						<Text
							fontSize="sm"
							fontWeight="medium"
							color="orange.600"
						>
							{fmt(record.reversible_amount)}
						</Text>
					</GridItem>
					<GridItem>
						<Text fontSize="xs" color="gray.500">
							Agent&apos;s E-value
						</Text>
						<Text fontSize="sm" fontWeight="medium">
							{fmt(record.agent_balance)}
						</Text>
					</GridItem>
				</Grid>
				<Box
					mt={3}
					pt={3}
					borderTop="1px solid"
					borderColor="orange.200"
				>
					<Text fontSize="xs" color="orange.700">
						<Text as="span" fontWeight="bold">
							Note:
						</Text>{" "}
						{isPartial
							? "Enter the partial amount you wish to reverse. It cannot exceed the reversible amount."
							: "The full reversible amount will be credited back to your wallet."}
					</Text>
				</Box>
			</Box>

			{/* Form fields */}
			<Box display="flex" flexDirection="column" gap={4}>
				<FormControl isInvalid={!!amountError}>
					<FormLabel fontSize="sm">Amount</FormLabel>
					<Input
						type="number"
						placeholder="Enter amount"
						value={amount}
						isReadOnly={!isPartial}
						bg={!isPartial ? "gray.50" : "white"}
						onChange={(e) => setAmount(e.target.value)}
						onBlur={() =>
							setTouched((t) => ({ ...t, amount: true }))
						}
					/>
					{amountError && (
						<FormErrorMessage>{amountError}</FormErrorMessage>
					)}
				</FormControl>

				<FormControl isInvalid={!!reasonError}>
					<FormLabel fontSize="sm">Reason</FormLabel>
					<Input
						placeholder="Enter reason for reversal"
						value={reason}
						onChange={(e) => setReason(e.target.value)}
						onBlur={() =>
							setTouched((t) => ({ ...t, reason: true }))
						}
					/>
					{reasonError && (
						<FormErrorMessage>{reasonError}</FormErrorMessage>
					)}
				</FormControl>

				{errorMessage && (
					<Box
						p={3}
						bg="red.50"
						border="1px solid"
						borderColor="red.200"
						borderRadius="md"
					>
						<Text fontSize="sm" color="red.600">
							{errorMessage}
						</Text>
					</Box>
				)}

				<Box display="flex" gap={3} justifyContent="flex-end" pt={2}>
					<Button
						variant="ghost"
						onClick={onBack}
						isDisabled={isSubmitting}
					>
						Back
					</Button>
					<Button
						colorScheme="orange"
						rightIcon={<Text>→</Text>}
						onClick={handleSubmit}
						isLoading={isSubmitting}
						loadingText="Processing..."
					>
						Proceed
					</Button>
				</Box>
			</Box>
		</Box>
	);
};
