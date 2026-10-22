import { PaddingBox } from "@/components/ui";
import PaymentsFlow from "@/screens/Payments/PaymentsFlow";

const AdminPaymentsPage = () => (
	<PaddingBox>
		<PaymentsFlow />
	</PaddingBox>
);

AdminPaymentsPage.pageMeta = {
	title: "Payments",
	isSubPage: true,
};

export default AdminPaymentsPage;
