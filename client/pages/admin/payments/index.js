import { PaddingBox } from "components";
import PaymentsFlow from "page-components/Payments/PaymentsFlow";

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
