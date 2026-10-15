import {
	Badge,
	Box,
	Button,
	Collapse,
	Flex,
	Grid,
	GridItem,
	HStack,
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
import { ReversibleRecord, ReverseType } from "../context/types";

const fmt = (n: number) =>
	`₹ ${Number(n).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;

interface ExpandedDetailProps {
	record: ReversibleRecord;
	showActions: boolean;
	onReverse: (_type: ReverseType) => void;
}

const ExpandedDetail = ({
	record,
	showActions,
	onReverse,
}: ExpandedDetailProps) => (
	<Box borderTop="1px solid" borderColor="gray.100" bg="white" px={4} py={3}>
		<Grid
			templateColumns={{ base: "1fr", md: "repeat(2, 1fr)" }}
			gap={3}
			mb={3}
		>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					Agent&apos;s Name
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.csp_name}
				</Text>
			</GridItem>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					Agent&apos;s Mobile Number
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.customer_id}
				</Text>
			</GridItem>
			<GridItem>
				<Text fontSize="xs" color="gray.500">
					E-value Transferred Date and Time
				</Text>
				<Text fontSize="sm" fontWeight="medium">
					{record.created_at}
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

		{showActions && record.reversible_amount > 0 && (
			<HStack justify="flex-end" spacing={3} mt={2}>
				<Button
					size="sm"
					colorScheme="orange"
					variant="solid"
					rightIcon={<Text>→</Text>}
					onClick={() => onReverse("partial")}
				>
					Reverse Partial Amount
				</Button>
				<Button
					size="sm"
					colorScheme="orange"
					variant="solid"
					rightIcon={<Text>→</Text>}
					onClick={() => onReverse("full")}
				>
					Reverse Full Amount
				</Button>
			</HStack>
		)}
	</Box>
);

interface RowProps {
	record: ReversibleRecord;
	showActions: boolean;
	onReverse: (_record: ReversibleRecord, _type: ReverseType) => void;
}

const TableRow = ({ record, showActions, onReverse }: RowProps) => {
	const { isOpen, onToggle } = useDisclosure();

	return (
		<>
			<Tr
				cursor="pointer"
				_hover={{ bg: "gray.50" }}
				onClick={onToggle}
				bg={isOpen ? "gray.50" : "white"}
			>
				<Td w="40px">
					<Badge
						colorScheme="orange"
						variant="subtle"
						fontSize="lg"
						cursor="pointer"
					>
						{isOpen ? "▾" : "•••"}
					</Badge>
				</Td>
				<Td fontSize="sm">{record.csp_code}</Td>
				<Td fontSize="sm" isNumeric>
					{fmt(record.value_transferred)}
				</Td>
				<Td fontSize="sm" isNumeric>
					{fmt(record.reverted_amount)}
				</Td>
				<Td fontSize="sm" isNumeric>
					{fmt(record.reversible_amount)}
				</Td>
				<Td fontSize="sm" isNumeric>
					{fmt(record.agent_balance)}
				</Td>
			</Tr>
			<Tr>
				<Td colSpan={6} p={0} border="none">
					<Collapse in={isOpen} animateOpacity>
						<ExpandedDetail
							record={record}
							showActions={showActions}
							onReverse={(type) => onReverse(record, type)}
						/>
					</Collapse>
				</Td>
			</Tr>
		</>
	);
};

interface ReversibleTableProps {
	records: ReversibleRecord[];
	isLoading: boolean;
	showActions?: boolean;
	emptyMessage: string;
	noteText: string;
	onReverse: (_record: ReversibleRecord, _type: ReverseType) => void;
}

export const ReversibleTable = ({
	records,
	isLoading,
	showActions = false,
	emptyMessage,
	noteText,
	onReverse,
}: ReversibleTableProps) => {
	if (isLoading) {
		return (
			<Flex justify="center" py={16}>
				<Spinner size="lg" color="blue.500" />
			</Flex>
		);
	}

	if (records.length === 0) {
		return (
			<Box py={10} textAlign="center">
				<Text fontSize="sm" color="gray.500">
					{emptyMessage}
				</Text>
			</Box>
		);
	}

	return (
		<Box>
			<Box overflowX="auto">
				<Table variant="simple" size="sm">
					<Thead>
						<Tr bg="gray.50">
							<Th w="40px" />
							<Th>Agent&apos;s User Code</Th>
							<Th isNumeric>E-value Transferred</Th>
							<Th isNumeric>Reversed Amount</Th>
							<Th isNumeric>Reversible Amount</Th>
							<Th isNumeric>Agent&apos;s E-value</Th>
						</Tr>
					</Thead>
					<Tbody>
						{records.map((r) => (
							<TableRow
								key={r.tid}
								record={r}
								showActions={showActions}
								onReverse={onReverse}
							/>
						))}
					</Tbody>
				</Table>
			</Box>
			<Box
				mt={4}
				px={2}
				py={2}
				bg="gray.50"
				borderRadius="md"
				border="1px solid"
				borderColor="gray.200"
			>
				<Text fontSize="xs" color="gray.600">
					<Text as="span" fontWeight="bold">
						Note:
					</Text>{" "}
					{noteText}
				</Text>
			</Box>
		</Box>
	);
};
