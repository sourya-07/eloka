import { fetcher } from "helpers/apiHelper";
import useRefreshToken from "hooks/useRefreshToken";
import {
	createContext,
	useCallback,
	useContext,
	useEffect,
	useMemo,
	useState,
} from "react";
import { useUser } from ".";

const DashboardDataContext = createContext();

/**
 * Builds the Active Agents API payload (interaction_type_id: 818).
 * Returns null if required user fields are missing, preventing premature calls.
 * Mirrors buildBalancePayload() in WalletContext.
 */
const buildActiveAgentsPayload = (userDetails) => {
	const code = userDetails?.code;
	const mobile = userDetails?.mobile;
	const orgId = userDetails?.org_id || "26";

	if (!code || !mobile) return null;

	return {
		interaction_type_id: 818,
		user_code: code,
		initiator_id: mobile,
		org_id: orgId,
	};
};

/**
 * Builds the Product Filter List API payload (interaction_type_id: 1044).
 * Returns null if required user fields are missing.
 */
const buildProductFilterPayload = (userDetails) => {
	const code = userDetails?.code;
	const mobile = userDetails?.mobile;
	const orgId = userDetails?.org_id || "26";

	if (!code || !mobile) return null;

	return {
		interaction_type_id: 1044,
		user_code: code,
		initiator_id: mobile,
		org_id: orgId,
	};
};

/**
 * Context provider that pre-fetches key dashboard data immediately after login.
 * Mirrors the WalletProvider pattern exactly: triggers on isLoggedIn && userCode,
 * ensuring dashboard APIs fire in the same post-login lifecycle as the Balance API.
 *
 * Pre-fetches on login:
 *   - Active Agents (interaction_type_id: 818) → used by BusinessDashboard TopPanel
 *   - Product Filter List (interaction_type_id: 1044) → used by all business widgets
 *
 * Both requests include user_code, initiator_id, org_id sourced from the same
 * user session as the Balance API (userData.userDetails).
 */
const DashboardDataProvider = ({ children }) => {
	const { userData, isLoggedIn, userId, accessTokenLite } = useUser();
	const { generateNewToken } = useRefreshToken();

	const [prefetchedActiveAgents, setPrefetchedActiveAgents] = useState(null);
	const [prefetchedProductFilter, setPrefetchedProductFilter] = useState([]);
	const [prefetchLoading, setPrefetchLoading] = useState(false);

	const userDetails = userData?.userDetails;

	const fetchDashboardData = useCallback(() => {
		const activeAgentsPayload = buildActiveAgentsPayload(userDetails);
		const productFilterPayload = buildProductFilterPayload(userDetails);

		if (!activeAgentsPayload || !productFilterPayload) {
			console.warn(
				"[DASHBOARD] Skipping prefetch: user_code or mobile not available yet"
			);
			return;
		}

		console.log("[DASHBOARD] login success — starting dashboard prefetch");
		setPrefetchLoading(true);

		// Pre-fetch Active Agents (818)
		console.log("[DASHBOARD] request", activeAgentsPayload);
		const p1 = fetcher(
			"/api/transaction",
			{
				token: accessTokenLite,
				body: activeAgentsPayload,
				timeout: 30000,
			},
			generateNewToken
		)
			.then((data) => {
				console.log("[DASHBOARD] response (Active Agents)", data);
				const raw =
					data?.data?.dashboard_object?.totalActiveData || {};
				setPrefetchedActiveAgents(raw);
			})
			.catch((err) => {
				console.error(
					"[DASHBOARD] Active Agents prefetch error:",
					err
				);
			});

		// Pre-fetch Product Filter List (1044)
		console.log("[DASHBOARD] request", productFilterPayload);
		const p2 = fetcher(
			"/api/transaction",
			{
				token: accessTokenLite,
				body: productFilterPayload,
				timeout: 30000,
			},
			generateNewToken
		)
			.then((data) => {
				console.log("[DASHBOARD] response (Product Filter)", data);
				const products = data?.param_attributes?.list_elements ?? [];
				const allOption = { label: "All Products", value: "" };
				const formatted = products.map((p) => ({
					label: p.label,
					value: p.tx_typeid,
				}));
				setPrefetchedProductFilter([allOption, ...formatted]);
			})
			.catch((err) => {
				console.error(
					"[DASHBOARD] Product Filter prefetch error:",
					err
				);
			});

		Promise.all([p1, p2]).finally(() => setPrefetchLoading(false));
	}, [accessTokenLite, userDetails, generateNewToken]);

	const userCode = userDetails?.code;

	// Trigger dashboard prefetch as soon as user is logged in and userCode is
	// available. Mirrors the exact same useEffect trigger in WalletContext:
	//   useEffect(() => { if (isLoggedIn && userCode) fetchBalance(); }, [...])
	useEffect(() => {
		if (isLoggedIn && userCode) {
			fetchDashboardData();
		}
	}, [isLoggedIn, userId, userCode, fetchDashboardData]);

	const contextValues = useMemo(
		() => ({
			prefetchedActiveAgents,
			prefetchedProductFilter,
			prefetchLoading,
			refreshDashboardData: fetchDashboardData,
		}),
		[
			prefetchedActiveAgents,
			prefetchedProductFilter,
			prefetchLoading,
			fetchDashboardData,
		]
	);

	return (
		<DashboardDataContext.Provider value={contextValues}>
			{children}
		</DashboardDataContext.Provider>
	);
};

/**
 * Hook to consume pre-fetched dashboard data.
 * Returns data that was fetched immediately after login, so dashboard
 * components can skip redundant API calls on first mount.
 */
const useDashboardData = () => {
	const context = useContext(DashboardDataContext);
	if (!context) {
		throw new Error(
			"useDashboardData must be used within a DashboardDataProvider"
		);
	}
	return context;
};

export { DashboardDataProvider, useDashboardData };
