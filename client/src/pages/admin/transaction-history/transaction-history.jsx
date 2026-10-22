import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { TransactionHistoryBreadcrumbs } from "@/constants";
import { TransactionHistory } from "@/screens/Admin";

const TransactionHistoryPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={TransactionHistoryBreadcrumbs}>
				<TransactionHistory />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

TransactionHistoryPage.pageMeta = {
	title: "Transaction History | Admin",
};

export default TransactionHistoryPage;
