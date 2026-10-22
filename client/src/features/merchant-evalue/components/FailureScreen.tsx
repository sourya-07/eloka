import { Box, Button, Flex, Stack, Text } from "@chakra-ui/react";

interface FailureScreenProps {
	errorMessage: string;
	onRetry: () => void;
	onBack: () => void;
}

export const FailureScreen = ({
	errorMessage,
	onRetry,
	onBack,
}: FailureScreenProps) => (
	<Flex direction="column" align="center" gap={6} py={6}>
		<Box
			bg="red.50"
			border="1px solid"
			borderColor="red.200"
			borderRadius="xl"
			p={6}
			w="full"
			maxW="480px"
		>
			<Flex direction="column" align="center" mb={4}>
				<Box bg="red.100" borderRadius="full" p={3} mb={3}>
					<Text fontSize="3xl">❌</Text>
				</Box>
				<Text fontSize="lg" fontWeight="bold" color="red.700">
					Transaction Failed
				</Text>
				<Text fontSize="sm" color="red.600" textAlign="center" mt={1}>
					{errorMessage}
				</Text>
			</Flex>
		</Box>

		<Stack spacing={3} w="full" maxW="480px">
			<Button colorScheme="blue" onClick={onRetry}>
				Retry
			</Button>
			<Button variant="outline" colorScheme="blue" onClick={onBack}>
				Back to Merchant E-value
			</Button>
		</Stack>
	</Flex>
);
