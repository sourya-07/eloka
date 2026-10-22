import { PaddingBox } from "@/components/ui";
import PaymentsFlow from "@/screens/Payments/PaymentsFlow";

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
