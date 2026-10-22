import { PaddingBox } from "@/components/ui";
import TransactionResult from "@/screens/Payments/TransactionResult";

const TransactionResultPage = () => {
	return (
		<PaddingBox>
			<TransactionResult />
		</PaddingBox>
	);
};

TransactionResultPage.pageMeta = {
	title: "Transaction Result",
	isSubPage: true,
};

export default TransactionResultPage;
