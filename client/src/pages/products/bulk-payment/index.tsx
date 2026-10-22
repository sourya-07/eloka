import { Box } from "@chakra-ui/react";
import { Breadcrumb } from "@/components/ui/Breadcrumb";
import { generateBreadcrumbs } from "@/components/ui/BreadcrumbWrapper";
import { useRouter } from "next/router";
import { BulkPayout } from "@/screens/products/bulk-payout";

/**
 * Entry point for the Bulk Payout page.
 * @returns {JSX.Element} Bulk Payout page
 */
export default function BulkPayoutPage() {
	const router = useRouter();

	const omitPaths = ["/products"];

	const crumbs = generateBreadcrumbs(router.asPath, {}, omitPaths);

	return (
		<>
			<Box
				p={{
					base: "0px",
					md: "30px",
				}}
				pb={{ base: "20px", md: "5px" }}
			>
				<Breadcrumb crumbs={crumbs} />
			</Box>
			<BulkPayout />
		</>
	);
}

BulkPayoutPage.pageMeta = {
	title: "Bulk Payments",
	isFixedBottomAppBar: true,
};
