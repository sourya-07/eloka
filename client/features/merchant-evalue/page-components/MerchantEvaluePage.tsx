import { Box, Spinner, Flex } from "@chakra-ui/react";
import { useCallback, useEffect } from "react";
import { FailureScreen } from "../components/FailureScreen";
import { HistoryGrid } from "../components/HistoryGrid";
import { SuccessScreen } from "../components/SuccessScreen";
import { TransferForm } from "../components/TransferForm";
import { MEVALUE_STEP } from "../constants";
import {
	MerchantEvalueProvider,
	useMerchantEvalue,
} from "../context/MerchantEvalueContext";
import { MerchantListItem } from "../context/types";
import { useMerchantEvalueApi } from "../hooks/useMerchantEvalueApi";

const MerchantEvalueInner = () => {
	const { state, dispatch } = useMerchantEvalue();
	const {
		fetchHistory,
		fetchMerchantList,
		transferEvalue,
		isLoadingHistory,
		isLoadingMerchants,
		isTransferring,
	} = useMerchantEvalueApi();

	const loadHistory = useCallback(async () => {
		dispatch({ type: "SET_STEP", payload: MEVALUE_STEP.LOADING_HISTORY });
		try {
			const records = await fetchHistory();
			dispatch({ type: "SET_HISTORY", payload: records });
			dispatch({
				type: "SET_STEP",
				payload:
					records.length === 0
						? MEVALUE_STEP.EMPTY_HISTORY
						: MEVALUE_STEP.HISTORY_AVAILABLE,
			});
		} catch {
			dispatch({ type: "SET_STEP", payload: MEVALUE_STEP.EMPTY_HISTORY });
		}
	}, [fetchHistory, dispatch]);

	useEffect(() => {
		loadHistory();
	}, [loadHistory]);

	const handleTransferClick = async () => {
		dispatch({ type: "SET_STEP", payload: MEVALUE_STEP.LOADING_MERCHANTS });
		dispatch({ type: "CLEAR_ERROR" });
		try {
			const merchants = await fetchMerchantList();
			dispatch({ type: "SET_MERCHANTS", payload: merchants });
			dispatch({ type: "SELECT_MERCHANT", payload: null });
			dispatch({
				type: "SET_STEP",
				payload: MEVALUE_STEP.MERCHANT_SELECTED,
			});
		} catch {
			dispatch({
				type: "SET_ERROR",
				payload: "Failed to load merchants. Please try again.",
			});
			dispatch({
				type: "SET_STEP",
				payload: MEVALUE_STEP.HISTORY_AVAILABLE,
			});
		}
	};

	const handleMerchantChange = (merchant: MerchantListItem | null) => {
		dispatch({ type: "SELECT_MERCHANT", payload: merchant });
	};

	const handleTransferSubmit = async (params: {
		amount: string;
		remarks: string;
		payment_mode: string;
	}) => {
		if (!state.selectedMerchant) return;
		dispatch({
			type: "SET_STEP",
			payload: MEVALUE_STEP.TRANSFER_IN_PROGRESS,
		});
		dispatch({ type: "CLEAR_ERROR" });
		try {
			const result = await transferEvalue({
				customer_id: state.selectedMerchant.value,
				...params,
			});
			dispatch({ type: "SET_TRANSFER_RESULT", payload: result });
			dispatch({
				type: "SET_STEP",
				payload: MEVALUE_STEP.TRANSFER_SUCCESS,
			});
		} catch (err: unknown) {
			const message =
				err instanceof Error
					? err.message
					: "Transfer failed. Please try again.";
			dispatch({ type: "SET_ERROR", payload: message });
			dispatch({
				type: "SET_STEP",
				payload: MEVALUE_STEP.TRANSFER_FAILED,
			});
		}
	};

	const handleRetry = () => {
		dispatch({ type: "SELECT_MERCHANT", payload: null });
		dispatch({ type: "CLEAR_ERROR" });
		dispatch({ type: "SET_STEP", payload: MEVALUE_STEP.MERCHANT_SELECTED });
	};

	const handleBackToHistory = () => {
		dispatch({ type: "CLEAR_ERROR" });
		loadHistory();
	};

	const handleRaiseQuery = () => {
		// Navigate to query/support — left as no-op per current project patterns
	};

	const { step } = state;

	if (
		step === MEVALUE_STEP.LOADING_HISTORY ||
		step === MEVALUE_STEP.LOADING_MERCHANTS
	) {
		return (
			<Flex justify="center" align="center" py={20}>
				<Spinner size="lg" color="blue.500" />
			</Flex>
		);
	}

	if (
		step === MEVALUE_STEP.EMPTY_HISTORY ||
		step === MEVALUE_STEP.HISTORY_AVAILABLE
	) {
		return (
			<HistoryGrid
				records={state.history}
				isLoading={isLoadingHistory}
				onTransferClick={handleTransferClick}
			/>
		);
	}

	if (step === MEVALUE_STEP.MERCHANT_SELECTED) {
		return (
			<TransferForm
				merchants={state.merchants}
				isLoadingMerchants={isLoadingMerchants}
				isTransferring={isTransferring}
				errorMessage={state.errorMessage}
				selectedMerchant={state.selectedMerchant}
				onMerchantChange={handleMerchantChange}
				onSubmit={handleTransferSubmit}
				onBack={handleBackToHistory}
			/>
		);
	}

	if (step === MEVALUE_STEP.TRANSFER_IN_PROGRESS) {
		return (
			<Flex
				direction="column"
				align="center"
				justify="center"
				py={20}
				gap={4}
			>
				<Spinner size="xl" color="blue.500" />
			</Flex>
		);
	}

	if (step === MEVALUE_STEP.TRANSFER_SUCCESS && state.transferResult) {
		return (
			<SuccessScreen
				result={state.transferResult}
				onRaiseQuery={handleRaiseQuery}
				onDone={() => {
					loadHistory();
				}}
			/>
		);
	}

	if (step === MEVALUE_STEP.TRANSFER_FAILED) {
		return (
			<FailureScreen
				errorMessage={
					state.errorMessage ?? "An unexpected error occurred."
				}
				onRetry={handleRetry}
				onBack={handleBackToHistory}
			/>
		);
	}

	return null;
};

export const MerchantEvaluePage = () => (
	<MerchantEvalueProvider>
		<Box p={4}>
			<MerchantEvalueInner />
		</Box>
	</MerchantEvalueProvider>
);
