import {
	Badge,
	Box,
	Button,
	Collapse,
	Flex,
	Grid,
	GridItem,
	Spinner,
	Table,
	Tbody,
	Td,
	Text,
	Th,
	Thead,
	Tr,
	useDisclosure,
} from "@chakra-ui/react";
import { EvalueHistoryRecord } from "../context/types";

interface ExpandedRowProps {
	record: EvalueHistoryRecord;
}

const ExpandedRow = ({ record }: ExpandedRowProps) => (
	<Box bg="gray.50" p={4} borderRadius="md" mt={2}>
		<Grid templateColumns="repeat(2, 1fr)" gap={3}>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					Transferred By
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.approved_by}
				</Text>
			</GridItem>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					Payment Mode
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.payment_mode}
				</Text>
			</GridItem>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					Amount Collected
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					₹{record.cash_collected?.toFixed(2)}
				</Text>
			</GridItem>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					Requested Date
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.created_at}
				</Text>
			</GridItem>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					Transferred Date
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.approved_at}
				</Text>
			</GridItem>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					Cash Collected Date
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.updated_at}
				</Text>
			</GridItem>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					Merchant Code
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.csp_code}
				</Text>
			</GridItem>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					TID
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.tid}
				</Text>
			</GridItem>
		</Grid>
	</Box>
);

interface HistoryRowProps {
	record: EvalueHistoryRecord;
}

const HistoryRow = ({ record }: HistoryRowProps) => {
	const { isOpen, onToggle } = useDisclosure();
	const isSuccess = record.tx_status === 1;

	return (
		<>
			<Tr cursor="pointer" _hover={{ bg: "gray.50" }} onClick={onToggle}>
				<Td fontSize="sm">{record.tid}</Td>
				<Td fontSize="sm">
					<Text fontWeight="medium">{record.csp_name}</Text>
					<Text fontSize="xs" color="gray.500">
						{record.shop}
					</Text>
				</Td>
				<Td fontSize="sm" isNumeric>
					₹{record.value_to_be_transferred?.toFixed(2)}
				</Td>
				<Td>
					<Badge
						colorScheme={isSuccess ? "green" : "red"}
						fontSize="xs"
					>
						{record.txstatus_desc}
					</Badge>
				</Td>
				<Td fontSize="sm" isNumeric>
					₹{record.value_transferred?.toFixed(2)}
				</Td>
				<Td fontSize="sm" isNumeric>
					₹{record.outstanding_amount?.toFixed(2)}
				</Td>
			</Tr>
			<Tr>
				<Td colSpan={6} p={0} border="none">
					<Collapse in={isOpen} animateOpacity>
						<Box px={4} pb={3}>
							<ExpandedRow record={record} />
						</Box>
					</Collapse>
				</Td>
			</Tr>
		</>
	);
};

interface HistoryGridProps {
	records: EvalueHistoryRecord[];
	isLoading: boolean;
	onTransferClick: () => void;
}

export const HistoryGrid = ({
	records,
	isLoading,
	onTransferClick,
}: HistoryGridProps) => {
	if (isLoading) {
		return (
			<Flex justify="center" align="center" py={16}>
				<Spinner size="lg" color="primary.500" />
			</Flex>
		);
	}

	return (
		<Box>
			<Flex justify="flex-end" mb={4}>
				<Button colorScheme="blue" size="sm" onClick={onTransferClick}>
					Transfer to Merchant
				</Button>
			</Flex>

			{records.length === 0 ? (
				<Flex
					direction="column"
					align="center"
					justify="center"
					py={16}
					gap={2}
				>
					<Text fontSize="md" fontWeight="semibold" color="gray.500">
						No E-value requests found
					</Text>
				</Flex>
			) : (
				<Box overflowX="auto">
					<Table variant="simple" size="sm">
						<Thead>
							<Tr bg="gray.50">
								<Th>Request TID</Th>
								<Th>Merchant Name</Th>
								<Th isNumeric>Requested Amount</Th>
								<Th>Status</Th>
								<Th isNumeric>E-value Transferred</Th>
								<Th isNumeric>Outstanding Amount</Th>
							</Tr>
						</Thead>
						<Tbody>
							{records.map((record) => (
								<HistoryRow key={record.tid} record={record} />
							))}
						</Tbody>
					</Table>
				</Box>
			)}
		</Box>
	);
};
