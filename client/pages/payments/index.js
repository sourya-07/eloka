import { PaddingBox } from "components";
import PaymentsFlow from "page-components/Payments/PaymentsFlow";

const PaymentsPage = () => (
	<PaddingBox>
		<PaymentsFlow />
	</PaddingBox>
);

PaymentsPage.pageMeta = {
	title: "Payments",
	isSubPage: true,
};

export default PaymentsPage;
