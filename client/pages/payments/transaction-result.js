import { PaddingBox } from "components";
import TransactionResult from "page-components/Payments/TransactionResult";

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
