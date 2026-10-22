import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { TransactionHistoryBreadcrumbs } from "@/constants";
import { DetailedStatement } from "@/screens/Admin";

const DetailedStatementPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={TransactionHistoryBreadcrumbs}>
				<DetailedStatement />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

DetailedStatementPage.pageMeta = {
	title: "Detailed Statement | Admin",
};

export default DetailedStatementPage;
