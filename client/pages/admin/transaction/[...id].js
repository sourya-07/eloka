import { EkoConnectWidget } from "components/EkoConnectWidget";
import PaymentsFlow from "page-components/Payments/PaymentsFlow";
import { useRouter } from "next/router";

// Payments module transaction type ID — renders the custom PaymentsFlow
const PAYMENTS_TRANSACTION_ID = 996;

/**
 * [For Admin] The transaction page component to render all financial transaction flows.
 * - For interaction_type_id 996 (Payments): renders the custom PaymentsFlow
 * - All other transactions: loads the connect-wlc-widget
 */
const TransactionPage = () => {
	const router = useRouter();
	const { id } = router.query;

	if (!(id && id.length > 0)) {
		return null;
	}

	// Starting transaction-type-id
	const start_id = parseInt(id[0]);

	if (start_id === PAYMENTS_TRANSACTION_ID) {
		return <PaymentsFlow />;
	}

	return <EkoConnectWidget start_id={start_id} paths={id} />;
};

TransactionPage.pageMeta = {
	title: "Transaction",
	isSubPage: false,
};

export default TransactionPage;
