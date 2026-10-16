import { Box, Center, Text } from "@chakra-ui/react";
import { Icon, PaddingBox } from "components";

const AepsPage = () => (
	<PaddingBox>
		<Center minH="60vh">
			<Box textAlign="center" maxW="360px" px={4}>
				<Center
					w={16}
					h={16}
					borderRadius="full"
					bg="orange.50"
					mx="auto"
					mb={4}
				>
					<Icon name="lock" size="lg" color="orange.400" />
				</Center>
				<Text fontSize="lg" fontWeight="bold" color="gray.700" mb={2}>
					Service Not Enabled
				</Text>
				<Text fontSize="sm" color="gray.500" lineHeight="tall">
					Aadhaar Enabled Payment System (AEPS) is not activated for
					your account. Please contact your administrator to get
					access.
				</Text>
			</Box>
		</Center>
	</PaddingBox>
);

AepsPage.pageMeta = {
	title: "AEPS",
	isSubPage: true,
};

export default AepsPage;
