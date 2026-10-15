import {
	Box,
	Button,
	Divider,
	Grid,
	GridItem,
	HStack,
	Icon,
	Text,
} from "@chakra-ui/react";
import { ReverseResult } from "../context/types";

const fmt = (n: string | number): string => {
	const num = typeof n === "string" ? parseFloat(n) : n;
	if (isNaN(num)) return String(n);
	return `₹ ${num.toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
};

interface SuccessCardProps {
	result: ReverseResult;
	onRaiseQuery: () => void;
	onDone: () => void;
}

const DetailRow = ({ label, value }: { label: string; value: string }) => (
	<GridItem>
		<Text fontSize="xs" color="gray.500">
			{label}
		</Text>
		<Text fontSize="sm" fontWeight="medium">
			{value}
		</Text>
	</GridItem>
);

export const SuccessCard = ({
	result,
	onRaiseQuery,
	onDone,
}: SuccessCardProps) => (
	<Box>
		{/* Header */}
		<Box
			textAlign="center"
			py={6}
			bg="green.50"
			borderRadius="md"
			border="1px solid"
			borderColor="green.200"
			mb={5}
		>
			<Box
				display="inline-flex"
				alignItems="center"
				justifyContent="center"
				w={12}
				h={12}
				bg="green.100"
				borderRadius="full"
				mb={3}
			>
				<Icon viewBox="0 0 24 24" w={6} h={6} color="green.500">
					<path
						fill="currentColor"
						d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"
					/>
				</Icon>
			</Box>
			<Text fontSize="lg" fontWeight="semibold" color="green.700">
				Reversal Successful
			</Text>
			<Text fontSize="sm" color="green.600" mt={1}>
				{result.message}
			</Text>
		</Box>

		{/* Details */}
		<Box
			p={4}
			bg="white"
			borderRadius="md"
			border="1px solid"
			borderColor="gray.200"
			mb={5}
		>
			<Grid templateColumns="repeat(2, 1fr)" gap={4}>
				<DetailRow label="Reversed Amount" value={fmt(result.amount)} />
				<DetailRow
					label="Requested Amount"
					value={fmt(result.requestedAmount)}
				/>
				<DetailRow label="TID" value={result.tid} />
				<DetailRow
					label="Transaction Time"
					value={result.transaction_time}
				/>
			</Grid>

			<Divider my={4} />

			<Grid templateColumns="repeat(2, 1fr)" gap={4}>
				<DetailRow label="Agent's Name" value={result.agent_name} />
				<DetailRow label="Agent's User Code" value={result.user_code} />
				<DetailRow
					label="Agent's E-value"
					value={fmt(result.csp_balance)}
				/>
				<DetailRow
					label="My E-value"
					value={fmt(result.saving_balance)}
				/>
			</Grid>
		</Box>

		{/* Actions */}
		<HStack justify="flex-end" spacing={3}>
			<Button
				variant="outline"
				colorScheme="orange"
				onClick={onRaiseQuery}
			>
				Raise Query
			</Button>
			<Button colorScheme="orange" onClick={onDone}>
				Done
			</Button>
		</HStack>
	</Box>
);
