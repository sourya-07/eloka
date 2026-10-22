import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { TransactionHistoryBreadcrumbs } from "@/constants";
import { AccountStatement } from "@/screens/Admin";

const AccountStatementPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={TransactionHistoryBreadcrumbs}>
				<AccountStatement />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

AccountStatementPage.pageMeta = {
	title: "Account Statement | Admin",
};

export default AccountStatementPage;
