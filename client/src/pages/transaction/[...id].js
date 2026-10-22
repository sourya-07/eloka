import { EkoConnectWidget } from "@/components/ui/EkoConnectWidget";
import PayoutForm from "@/screens/Payout/PayoutForm";
import PaymentsFlow from "@/screens/Payments/PaymentsFlow";
import { useRouter } from "next/router";

// Payout transaction type ID — renders the custom PayoutForm instead of the Connect widget
const PAYOUT_TRANSACTION_ID = 392;
// Payments module transaction type ID
const PAYMENTS_TRANSACTION_ID = 996;

/**
 * The transaction page component to render all financial transaction flows.
 * - For interaction_type_id 392 (Payout): renders the custom PayoutForm
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

	if (start_id === PAYOUT_TRANSACTION_ID) {
		return <PayoutForm />;
	}

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
