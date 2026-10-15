import {
	AlertDialog,
	AlertDialogBody,
	AlertDialogContent,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogOverlay,
	Box,
	Button,
	Flex,
	Spinner,
	Text,
	useDisclosure,
} from "@chakra-ui/react";
import { useRef, useState } from "react";
import { LuTrash2 } from "react-icons/lu";
import { avatarColor, initials, maskAccount } from "./utils";

const Avatar = ({ name, size = 10 }) => {
	const { text, bg } = avatarColor(name);
	return (
		<Flex
			w={size}
			h={size}
			borderRadius="full"
			bg={bg}
			align="center"
			justify="center"
			flexShrink={0}
		>
			<Text
				fontSize={size >= 12 ? "lg" : "sm"}
				fontWeight="bold"
				color={text}
				lineHeight="1"
			>
				{initials(name)}
			</Text>
		</Flex>
	);
};

const StatusDot = ({ active }) => (
	<Flex align="center" gap={1.5}>
		<Box
			w={1.5}
			h={1.5}
			borderRadius="full"
			bg={active ? "green.500" : "orange.400"}
			flexShrink={0}
		/>
		<Text
			fontSize="xxs"
			fontWeight="semibold"
			color={active ? "green.600" : "orange.500"}
			textTransform="uppercase"
			letterSpacing="wide"
		>
			{active ? "Active" : "Pending"}
		</Text>
	</Flex>
);

const RecipientCard = ({ recipient, onSelect, onDelete, isDeleting }) => (
	<Box
		w="full"
		border="1px solid"
		borderColor="gray.200"
		borderRadius="xl"
		bg="white"
		transition="all 0.15s"
		_hover={{ borderColor: "blue.300", boxShadow: "sh-card" }}
		overflow="hidden"
	>
		<Flex align="stretch">
			{/* Main clickable area — selects recipient */}
			<Box
				flex={1}
				p={4}
				cursor="pointer"
				_hover={{ bg: "blue.50" }}
				_active={{ bg: "blue.100" }}
				onClick={onSelect}
				minW={0}
			>
				<Flex align="center" gap={4}>
					<Avatar name={recipient.name} size={10} />

					<Box flex={1} minW={0}>
						<Text
							fontWeight="semibold"
							color="gray.800"
							fontSize="sm"
							noOfLines={1}
						>
							{recipient.name}
						</Text>
						<Text
							fontSize="xs"
							color="gray.500"
							mt={0.5}
							noOfLines={1}
						>
							{recipient.bank}
						</Text>
						<Text
							fontSize="xs"
							color="gray.400"
							fontFamily="mono"
							mt={0.5}
							letterSpacing="wider"
						>
							{maskAccount(recipient.account)}
							{recipient.ifsc && (
								<Text as="span" ml={2} color="gray.300">
									· {recipient.ifsc}
								</Text>
							)}
						</Text>
					</Box>

					<Flex
						direction="column"
						align="flex-end"
						gap={2}
						flexShrink={0}
					>
						<StatusDot active={recipient.status === 0} />
						<Text fontSize="xl" color="blue.300" lineHeight="1">
							›
						</Text>
					</Flex>
				</Flex>
			</Box>

			{/* Delete action — separated to avoid nested button */}
			<Flex
				align="center"
				justify="center"
				px={3}
				borderLeft="1px solid"
				borderColor="gray.100"
				cursor={isDeleting ? "not-allowed" : "pointer"}
				color="gray.400"
				transition="all 0.15s"
				_hover={
					isDeleting
						? {}
						: { bg: "red.50", color: "red.500", borderColor: "red.100" }
				}
				_active={isDeleting ? {} : { bg: "red.100" }}
				onClick={isDeleting ? undefined : onDelete}
				flexShrink={0}
				minW="48px"
				role="button"
				aria-label="Delete recipient"
			>
				{isDeleting ? (
					<Spinner size="sm" color="red.400" />
				) : (
					<LuTrash2 size={16} />
				)}
			</Flex>
		</Flex>
	</Box>
);

/**
 * RecipientList — shows saved recipients for the selected vendor.
 * Each card navigates to SendMoney on click; trash icon opens delete confirmation.
 */
const RecipientList = ({
	recipients,
	onSelectRecipient,
	onAddRecipient,
	onDeleteRecipient,
	deletingRecipientId,
}) => {
	const [recipientToDelete, setRecipientToDelete] = useState(null);
	const { isOpen, onOpen, onClose } = useDisclosure();
	const cancelRef = useRef();

	const handleDeleteClick = (recipient) => {
		setRecipientToDelete(recipient);
		onOpen();
	};

	const handleConfirmDelete = () => {
		if (!recipientToDelete) return;
		onClose();
		onDeleteRecipient(recipientToDelete);
		setRecipientToDelete(null);
	};

	return (
		<Box>
			{/* Header */}
			<Flex justify="space-between" align="center" mb={5}>
				<Box>
					<Text fontSize="lg" fontWeight="bold" color="gray.800">
						Recipients
					</Text>
					<Text fontSize="sm" color="gray.400" mt={0.5}>
						{recipients.length}{" "}
						{recipients.length === 1 ? "account" : "accounts"} saved
					</Text>
				</Box>
				<Button
					size="sm"
					colorScheme="blue"
					variant="outline"
					borderRadius="full"
					fontWeight="semibold"
					px={4}
					onClick={onAddRecipient}
				>
					+ Add New
				</Button>
			</Flex>

			{recipients.length === 0 ? (
				<Box
					textAlign="center"
					py={14}
					border="2px dashed"
					borderColor="gray.200"
					borderRadius="2xl"
					color="gray.400"
				>
					<Text fontSize="2xl" mb={2}>
						—
					</Text>
					<Text fontWeight="medium" color="gray.500">
						No recipients yet
					</Text>
					<Text fontSize="sm" color="gray.400" mt={1}>
						Add a recipient to start sending money
					</Text>
				</Box>
			) : (
				<Flex direction="column" gap={3}>
					{recipients.map((r) => (
						<RecipientCard
							key={r.recipient_id}
							recipient={r}
							onSelect={() => onSelectRecipient(r)}
							onDelete={() => handleDeleteClick(r)}
							isDeleting={deletingRecipientId === r.recipient_id}
						/>
					))}
				</Flex>
			)}

			{/* Delete confirmation dialog */}
			<AlertDialog
				isOpen={isOpen}
				leastDestructiveRef={cancelRef}
				onClose={onClose}
				isCentered
			>
				<AlertDialogOverlay>
					<AlertDialogContent borderRadius="xl" mx={4}>
						<AlertDialogHeader
							fontSize="md"
							fontWeight="bold"
							color="gray.800"
							pb={2}
						>
							Delete Recipient
						</AlertDialogHeader>

						<AlertDialogBody fontSize="sm" color="gray.600">
							Are you sure you want to delete{" "}
							<Text
								as="span"
								fontWeight="semibold"
								color="gray.800"
							>
								{recipientToDelete?.name}
							</Text>
							? This action cannot be undone.
						</AlertDialogBody>

						<AlertDialogFooter gap={2}>
							<Button
								ref={cancelRef}
								variant="outline"
								borderRadius="full"
								size="sm"
								onClick={onClose}
							>
								Cancel
							</Button>
							<Button
								colorScheme="red"
								borderRadius="full"
								size="sm"
								onClick={handleConfirmDelete}
							>
								Delete
							</Button>
						</AlertDialogFooter>
					</AlertDialogContent>
				</AlertDialogOverlay>
			</AlertDialog>
		</Box>
	);
};

export default RecipientList;
