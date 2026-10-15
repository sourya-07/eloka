import { PaddingBox } from "components";
import TransactionResult from "page-components/Payments/TransactionResult";

const AdminTransactionResultPage = () => {
	return (
		<PaddingBox>
			<TransactionResult />
		</PaddingBox>
	);
};

AdminTransactionResultPage.pageMeta = {
	title: "Transaction Result",
	isSubPage: true,
};

export default AdminTransactionResultPage;
