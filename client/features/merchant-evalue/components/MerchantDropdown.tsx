import { Box, Flex, Select, Spinner, Text } from "@chakra-ui/react";
import { MerchantListItem } from "../context/types";

interface MerchantDropdownProps {
	merchants: MerchantListItem[];
	selected: MerchantListItem | null;
	isLoading: boolean;
	onChange: (_merchant: MerchantListItem | null) => void;
}

export const MerchantDropdown = ({
	merchants,
	selected,
	isLoading,
	onChange,
}: MerchantDropdownProps) => {
	if (isLoading) {
		return (
			<Flex align="center" gap={2} py={2}>
				<Spinner size="sm" />
				<Text fontSize="sm" color="gray.500">
					Loading merchants…
				</Text>
			</Flex>
		);
	}

	const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
		const val = e.target.value;
		const found = merchants.find((m) => m.value === val) ?? null;
		onChange(found);
	};

	return (
		<Box>
			<Text fontSize="sm" fontWeight="medium" mb={1}>
				Select Merchant
			</Text>
			<Select
				placeholder="-- Select a merchant --"
				value={selected?.value ?? ""}
				onChange={handleChange}
				size="md"
			>
				{merchants.map((m) => {
					const parts = m.label.split(" - ");
					const name = parts[0] ?? m.label;
					const mobile = parts[1] ?? m.value;
					const code = parts[2] ?? m.csp_code;
					const shop = parts[3] ?? m.shop;
					return (
						<option key={m.value} value={m.value}>
							{name} | {mobile} | {code} | {shop}
						</option>
					);
				})}
			</Select>
		</Box>
	);
};
