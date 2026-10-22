import {
	Box,
	Button,
	Divider,
	Flex,
	Grid,
	GridItem,
	Stack,
	Text,
} from "@chakra-ui/react";
import { TransferResult } from "../context/types";

interface SuccessScreenProps {
	result: TransferResult;
	onRaiseQuery: () => void;
	onDone: () => void;
}

const ResultRow = ({ label, value }: { label: string; value: string }) => (
	<GridItem>
		<Text fontSize="xs" color="gray.500">
			{label}
		</Text>
		<Text fontSize="sm" fontWeight="semibold">
			{value}
		</Text>
	</GridItem>
);

export const SuccessScreen = ({
	result,
	onRaiseQuery,
	onDone,
}: SuccessScreenProps) => {
	const handlePrint = () => {
		window.print();
	};

	const handleWhatsApp = () => {
		const text = encodeURIComponent(
			`E-value Transfer Successful!\nTID: ${result.tid}\nAmount: ₹${result.amount}\nMerchant Mobile: ${result.customer_id}\nUpdated Balance: ₹${result.balance}`
		);
		window.open(`https://wa.me/?text=${text}`, "_blank");
	};

	return (
		<Flex direction="column" align="center" gap={6} py={6}>
			<Box
				bg="green.50"
				border="1px solid"
				borderColor="green.200"
				borderRadius="xl"
				p={6}
				w="full"
				maxW="480px"
			>
				<Flex direction="column" align="center" mb={4}>
					<Box bg="green.100" borderRadius="full" p={3} mb={3}>
						<Text fontSize="3xl">✅</Text>
					</Box>
					<Text fontSize="lg" fontWeight="bold" color="green.700">
						Transaction Successful
					</Text>
					<Text fontSize="sm" color="green.600" textAlign="center">
						{result.message}
					</Text>
				</Flex>

				<Divider mb={4} />

				<Grid templateColumns="repeat(2, 1fr)" gap={3}>
					<ResultRow label="TID" value={result.tid} />
					<ResultRow
						label="Transferred Amount"
						value={`₹${result.amount}`}
					/>
					<ResultRow
						label="Merchant Mobile"
						value={result.customer_id}
					/>
					<ResultRow
						label="Updated Balance"
						value={`₹${result.balance}`}
					/>
				</Grid>
			</Box>

			<Stack spacing={3} w="full" maxW="480px">
				<Button
					variant="outline"
					colorScheme="blue"
					onClick={handlePrint}
				>
					Print
				</Button>
				<Button
					colorScheme="whatsapp"
					bg="green.500"
					color="white"
					_hover={{ bg: "green.600" }}
					onClick={handleWhatsApp}
				>
					Share on WhatsApp
				</Button>
				<Button
					variant="ghost"
					colorScheme="orange"
					onClick={onRaiseQuery}
				>
					Raise Query
				</Button>
				<Button colorScheme="blue" variant="solid" onClick={onDone}>
					Back to Merchant E-value
				</Button>
			</Stack>
		</Flex>
	);
};
