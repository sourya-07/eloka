import { useUser } from "contexts";
import { fetcher } from "helpers/apiHelper";
import { useCallback, useState } from "react";
import { LIST_STATUS, REVERSE_EVALUE_INTERACTION } from "../constants";
import { ReversibleRecord, ReverseResult } from "../context/types";

const buildBase = (userDetails: {
	code: string;
	mobile: string;
	org_id?: string;
}) => {
	const orgId = userDetails?.org_id || "26";
	return {
		bc: orgId,
		user_code: userDetails.code,
		initiator_id: userDetails.mobile,
		org_id: orgId,
		source: "NEWCONNECT",
		locale: "en",
		lang: "null",
		version: "v2",
		latlong: "",
	};
};

const parseRecords = (data: unknown): ReversibleRecord[] => {
	const list = (data as { merchantevaluerequest?: ReversibleRecord[] })
		?.merchantevaluerequest;
	return Array.isArray(list) ? list : [];
};

export const useReverseEvalueApi = () => {
	const { userData, accessToken } = useUser();
	const userDetails = userData?.userDetails as {
		code: string;
		mobile: string;
		org_id?: string;
	};

	const [isLoadingReversible, setIsLoadingReversible] = useState(false);
	const [isLoadingHistory, setIsLoadingHistory] = useState(false);
	const [isSubmitting, setIsSubmitting] = useState(false);

	const fetchReversible = useCallback(async (): Promise<
		ReversibleRecord[]
	> => {
		setIsLoadingReversible(true);
		try {
			const data = await fetcher("/api/transaction", {
				token: accessToken,
				body: {
					...buildBase(userDetails),
					interaction_type_id: REVERSE_EVALUE_INTERACTION.GET_LIST,
					status: LIST_STATUS.REVERSIBLE,
				},
			});
			return parseRecords(data?.data ?? data);
		} finally {
			setIsLoadingReversible(false);
		}
	}, [accessToken, userDetails]);

	const fetchHistory = useCallback(async (): Promise<ReversibleRecord[]> => {
		setIsLoadingHistory(true);
		try {
			const data = await fetcher("/api/transaction", {
				token: accessToken,
				body: {
					...buildBase(userDetails),
					interaction_type_id: REVERSE_EVALUE_INTERACTION.GET_LIST,
					status: LIST_STATUS.HISTORY,
				},
			});
			return parseRecords(data?.data ?? data);
		} finally {
			setIsLoadingHistory(false);
		}
	}, [accessToken, userDetails]);

	const reverseEvalue = useCallback(
		async (params: {
			reference_tid: string;
			amount: string;
			reversible_amount: string;
			agent_balance: string;
			reason: string;
		}): Promise<ReverseResult> => {
			setIsSubmitting(true);
			try {
				const data = await fetcher("/api/transaction", {
					token: accessToken,
					body: {
						...buildBase(userDetails),
						interaction_type_id: REVERSE_EVALUE_INTERACTION.REVERSE,
						reference_tid: params.reference_tid,
						amount: params.amount,
						reversible_amount: params.reversible_amount,
						agent_balance: params.agent_balance,
						reason: params.reason,
					},
				});

				if (data?.response_status_id !== 0) {
					throw new Error(
						data?.message || "Reversal failed. Please try again."
					);
				}

				return {
					tid: String(data.data?.tid ?? ""),
					amount: String(data.data?.amount ?? ""),
					requestedAmount: String(data.data?.requestedAmount ?? ""),
					saving_balance: String(data.data?.saving_balance ?? ""),
					csp_balance: String(data.data?.csp_balance ?? ""),
					agent_name: String(data.data?.agent_name ?? ""),
					user_code: String(data.data?.user_code ?? ""),
					transaction_time: String(data.data?.transaction_time ?? ""),
					message: data.message || "E-value Successfully Reversed.",
				};
			} finally {
				setIsSubmitting(false);
			}
		},
		[accessToken, userDetails]
	);

	return {
		fetchReversible,
		fetchHistory,
		reverseEvalue,
		isLoadingReversible,
		isLoadingHistory,
		isSubmitting,
	};
};
