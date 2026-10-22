import {
	Box,
	Tab,
	TabList,
	TabPanel,
	TabPanels,
	Tabs,
	Text,
} from "@chakra-ui/react";
import { useEffect } from "react";
import { ReversibleTable } from "../components/ReversibleTable";
import { ReverseForm } from "../components/ReverseForm";
import { SuccessCard } from "../components/SuccessCard";
import { useReverseEvalue } from "../context/ReverseEvalueContext";
import { ReversibleRecord, ReverseType } from "../context/types";
import { useReverseEvalueApi } from "../hooks/useReverseEvalueApi";

const NOTE_REVERSIBLE =
	"Only transactions with a reversible amount greater than zero can be reversed. The reversal will be credited back to your wallet.";
const NOTE_HISTORY =
	"This is a record of all previously reversed e-value transactions.";

const EMPTY_REVERSIBLE = "No reversible transactions found.";
const EMPTY_HISTORY = "No reversal history found.";

export const ReverseEvaluePage = () => {
	const { state, dispatch } = useReverseEvalue();
	const {
		fetchReversible,
		fetchHistory,
		reverseEvalue,
		isLoadingReversible,
		isLoadingHistory,
		isSubmitting,
	} = useReverseEvalueApi();

	// Load reversible list on mount
	useEffect(() => {
		fetchReversible().then((records) =>
			dispatch({ type: "SET_REVERSIBLE", payload: records })
		);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	const handleTabChange = (index: number) => {
		const tab = index === 0 ? "reversible" : "history";
		dispatch({ type: "SET_TAB", payload: tab });
		if (
			tab === "history" &&
			state.historyRecords.length === 0 &&
			!isLoadingHistory
		) {
			fetchHistory().then((records) =>
				dispatch({ type: "SET_HISTORY", payload: records })
			);
		}
	};

	const handleReverse = (record: ReversibleRecord, type: ReverseType) => {
		dispatch({ type: "OPEN_FORM", payload: { record, reverseType: type } });
	};

	const handleFormSubmit = async (params: {
		amount: string;
		reason: string;
	}) => {
		if (!state.selectedRecord) return;
		dispatch({ type: "CLEAR_ERROR" });
		try {
			const result = await reverseEvalue({
				reference_tid: String(
					state.selectedRecord.reference_tid ||
						state.selectedRecord.tid
				),
				amount: params.amount,
				reversible_amount: String(
					state.selectedRecord.reversible_amount
				),
				agent_balance: String(state.selectedRecord.agent_balance),
				reason: params.reason,
			});
			dispatch({ type: "SET_RESULT", payload: result });
			// Refresh reversible list in background
			fetchReversible().then((records) =>
				dispatch({ type: "SET_REVERSIBLE", payload: records })
			);
		} catch (err) {
			dispatch({
				type: "SET_ERROR",
				payload:
					err instanceof Error
						? err.message
						: "Reversal failed. Please try again.",
			});
		}
	};

	const handleDone = () => {
		dispatch({ type: "SET_VIEW", payload: "list" });
	};

	const handleRaiseQuery = () => {
		// Placeholder — extend when raise-query flow is available
	};

	if (state.view === "form" && state.selectedRecord && state.reverseType) {
		return (
			<Box p={{ base: 4, md: 6 }}>
				<ReverseForm
					record={state.selectedRecord}
					reverseType={state.reverseType}
					isSubmitting={isSubmitting}
					errorMessage={state.errorMessage}
					onSubmit={handleFormSubmit}
					onBack={() =>
						dispatch({ type: "SET_VIEW", payload: "list" })
					}
				/>
			</Box>
		);
	}

	if (state.view === "success" && state.reverseResult) {
		return (
			<Box p={{ base: 4, md: 6 }}>
				<SuccessCard
					result={state.reverseResult}
					onRaiseQuery={handleRaiseQuery}
					onDone={handleDone}
				/>
			</Box>
		);
	}

	return (
		<Box p={{ base: 4, md: 6 }}>
			<Text fontSize="xl" fontWeight="semibold" mb={5}>
				Reverse E-Value
			</Text>

			<Tabs
				index={state.activeTab === "reversible" ? 0 : 1}
				onChange={handleTabChange}
				colorScheme="orange"
				variant="enclosed"
			>
				<TabList>
					<Tab fontWeight="medium">Reversible Transactions</Tab>
					<Tab fontWeight="medium">Reversal History</Tab>
				</TabList>

				<TabPanels>
					<TabPanel px={0} pt={4}>
						<ReversibleTable
							records={state.reversibleRecords}
							isLoading={isLoadingReversible}
							showActions
							emptyMessage={EMPTY_REVERSIBLE}
							noteText={NOTE_REVERSIBLE}
							onReverse={handleReverse}
						/>
					</TabPanel>

					<TabPanel px={0} pt={4}>
						<ReversibleTable
							records={state.historyRecords}
							isLoading={isLoadingHistory}
							showActions={false}
							emptyMessage={EMPTY_HISTORY}
							noteText={NOTE_HISTORY}
							onReverse={handleReverse}
						/>
					</TabPanel>
				</TabPanels>
			</Tabs>
		</Box>
	);
};
