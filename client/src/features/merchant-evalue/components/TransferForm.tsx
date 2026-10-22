import {
	Alert,
	AlertDescription,
	AlertIcon,
	Box,
	Button,
	Flex,
	FormControl,
	FormErrorMessage,
	FormLabel,
	Input,
	Radio,
	RadioGroup,
	Stack,
	Text,
	Textarea,
} from "@chakra-ui/react";
import { useState } from "react";
import { PAYMENT_MODE } from "../constants";
import { MerchantListItem } from "../context/types";
import { MerchantDropdown } from "./MerchantDropdown";
import { MerchantInfoCard } from "./MerchantInfoCard";

interface TransferFormProps {
	merchants: MerchantListItem[];
	isLoadingMerchants: boolean;
	isTransferring: boolean;
	errorMessage: string | null;
	selectedMerchant: MerchantListItem | null;
	onMerchantChange: (_merchant: MerchantListItem | null) => void;
	onSubmit: (_params: {
		amount: string;
		remarks: string;
		payment_mode: string;
	}) => void;
	onBack: () => void;
}

export const TransferForm = ({
	merchants,
	isLoadingMerchants,
	isTransferring,
	errorMessage,
	selectedMerchant,
	onMerchantChange,
	onSubmit,
	onBack,
}: TransferFormProps) => {
	const [amount, setAmount] = useState("");
	const [remarks, setRemarks] = useState("");
	const [paymentMode, setPaymentMode] = useState<string>(PAYMENT_MODE.CASH);
	const [errors, setErrors] = useState<{
		amount?: string;
		merchant?: string;
	}>({});

	const validate = () => {
		const newErrors: typeof errors = {};
		if (!selectedMerchant) {
			newErrors.merchant = "Please select a merchant.";
		}
		if (!amount || isNaN(Number(amount)) || Number(amount) <= 0) {
			newErrors.amount = "Enter a valid amount.";
		}
		setErrors(newErrors);
		return Object.keys(newErrors).length === 0;
	};

	const handleSubmit = () => {
		if (!validate()) return;
		onSubmit({ amount, remarks, payment_mode: paymentMode });
	};

	return (
		<Box>
			<Flex align="center" gap={2} mb={4}>
				<Button
					variant="ghost"
					size="sm"
					onClick={onBack}
					aria-label="Back to history"
				>
					← Back
				</Button>
				<Text fontWeight="bold" fontSize="md">
					Transfer to Merchant
				</Text>
			</Flex>

			<Stack spacing={4}>
				<MerchantDropdown
					merchants={merchants}
					selected={selectedMerchant}
					isLoading={isLoadingMerchants}
					onChange={onMerchantChange}
				/>
				{errors.merchant && (
					<Text fontSize="xs" color="red.500">
						{errors.merchant}
					</Text>
				)}

				{selectedMerchant && (
					<MerchantInfoCard merchant={selectedMerchant} />
				)}

				<FormControl isInvalid={!!errors.amount}>
					<FormLabel fontSize="sm">Amount (₹)</FormLabel>
					<Input
						type="number"
						placeholder="Enter amount"
						value={amount}
						onChange={(e) => setAmount(e.target.value)}
						min={1}
					/>
					{errors.amount && (
						<FormErrorMessage>{errors.amount}</FormErrorMessage>
					)}
				</FormControl>

				<FormControl>
					<FormLabel fontSize="sm">Remarks (optional)</FormLabel>
					<Textarea
						placeholder="Enter remarks"
						value={remarks}
						onChange={(e) => setRemarks(e.target.value)}
						rows={2}
					/>
				</FormControl>

				<FormControl>
					<FormLabel fontSize="sm">Payment Mode</FormLabel>
					<RadioGroup value={paymentMode} onChange={setPaymentMode}>
						<Stack direction="row" spacing={4}>
							<Radio value={PAYMENT_MODE.CASH}>Cash</Radio>
							<Radio value={PAYMENT_MODE.CREDIT}>Credit</Radio>
						</Stack>
					</RadioGroup>
				</FormControl>

				{errorMessage && (
					<Alert status="error" borderRadius="md">
						<AlertIcon />
						<AlertDescription fontSize="sm">
							{errorMessage}
						</AlertDescription>
					</Alert>
				)}

				<Button
					colorScheme="blue"
					onClick={handleSubmit}
					isLoading={isTransferring}
					loadingText="Processing…"
					isDisabled={isTransferring}
				>
					Proceed
				</Button>
			</Stack>
		</Box>
	);
};
