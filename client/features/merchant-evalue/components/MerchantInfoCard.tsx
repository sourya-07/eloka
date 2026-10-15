import { Box, Divider, Grid, GridItem, Text } from "@chakra-ui/react";
import { MerchantListItem } from "../context/types";

interface MerchantInfoCardProps {
	merchant: MerchantListItem;
}

const InfoRow = ({
	label,
	value,
}: {
	label: string;
	value: string | number;
}) => (
	<GridItem>
		<Text fontSize="xs" color="gray.500">
			{label}
		</Text>
		<Text fontSize="sm" fontWeight="semibold">
			{value}
		</Text>
	</GridItem>
);

export const MerchantInfoCard = ({ merchant }: MerchantInfoCardProps) => {
	const maxTransferable =
		merchant.outstanding_limit - merchant.outstanding_amount;

	return (
		<Box
			border="1px solid"
			borderColor="gray.200"
			borderRadius="lg"
			p={4}
			bg="blue.50"
		>
			<Text fontSize="sm" fontWeight="bold" color="blue.700" mb={3}>
				Merchant Details
			</Text>
			<Grid templateColumns="repeat(2, 1fr)" gap={3}>
				<InfoRow
					label="Merchant Balance"
					value={`₹${merchant.balance?.toFixed(2)}`}
				/>
				<InfoRow label="Merchant Shop" value={merchant.shop} />
				<InfoRow
					label="Outstanding Limit"
					value={`₹${merchant.outstanding_limit?.toFixed(2)}`}
				/>
				<InfoRow
					label="Outstanding Amount"
					value={`₹${merchant.outstanding_amount?.toFixed(2)}`}
				/>
				<InfoRow
					label="Cash Executive Name"
					value={merchant.cce_name}
				/>
			</Grid>
			<Divider my={3} />
			<Box bg="green.50" borderRadius="md" p={2} textAlign="center">
				<Text fontSize="xs" color="green.600">
					Maximum E-value Transferable
				</Text>
				<Text fontSize="lg" fontWeight="bold" color="green.700">
					₹{maxTransferable?.toFixed(2)}
				</Text>
			</Box>
		</Box>
	);
};
