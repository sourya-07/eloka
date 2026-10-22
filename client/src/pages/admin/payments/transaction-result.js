import { PaddingBox } from "@/components/ui";
import TransactionResult from "@/screens/Payments/TransactionResult";

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
