import { useUser } from "@/contexts";
import { fetcher } from "@/lib/apiHelper";
import { useCallback, useState } from "react";
import { MEVALUE_INTERACTION } from "../constants";
import {
	EvalueHistoryRecord,
	MerchantListItem,
	TransferResult,
} from "../context/types";

const buildBasePayload = (userDetails: {
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
	};
};

const parseDependentParams = (
	dependentParams: Array<{ name: string; value: string | number }>
): Record<string, string | number> => {
	return dependentParams.reduce<Record<string, string | number>>(
		(acc, param) => {
			acc[param.name] = param.value;
			return acc;
		},
		{}
	);
};

export const useMerchantEvalueApi = () => {
	const { userData, accessToken } = useUser();
	const userDetails = userData?.userDetails as {
		code: string;
		mobile: string;
		org_id?: string;
	};

	const [isLoadingHistory, setIsLoadingHistory] = useState(false);
	const [isLoadingMerchants, setIsLoadingMerchants] = useState(false);
	const [isTransferring, setIsTransferring] = useState(false);

	const fetchHistory = useCallback(async (): Promise<
		EvalueHistoryRecord[]
	> => {
		setIsLoadingHistory(true);
		try {
			const payload = {
				...buildBasePayload(userDetails),
				interaction_type_id: MEVALUE_INTERACTION.HISTORY,
			};
			const data = await fetcher("/api/transaction", {
				token: accessToken,
				body: payload,
			});
			return (data?.merchantevaluerequest as EvalueHistoryRecord[]) ?? [];
		} finally {
			setIsLoadingHistory(false);
		}
	}, [accessToken, userDetails]);

	const fetchMerchantList = useCallback(async (): Promise<
		MerchantListItem[]
	> => {
		setIsLoadingMerchants(true);
		try {
			const payload = {
				...buildBasePayload(userDetails),
				interaction_type_id: MEVALUE_INTERACTION.MERCHANT_LIST,
				config_value: "0",
			};
			const data = await fetcher("/api/transaction", {
				token: accessToken,
				body: payload,
			});

			const listElements =
				(data?.param_attributes?.list_elements as Array<{
					label: string;
					value: string;
					dependent_params: Array<{
						name: string;
						value: string | number;
					}>;
				}>) ?? [];

			return listElements.map((el) => {
				const params = parseDependentParams(el.dependent_params ?? []);
				return {
					label: el.label,
					value: el.value,
					balance: Number(params.balance ?? 0),
					shop: String(params.shop ?? ""),
					csp_code: String(params.csp_code ?? ""),
					cce_name: String(params.cce_name ?? "NA"),
					outstanding_limit: Number(params.outstanding_limit ?? 0),
					outstanding_amount: Number(params.outstanding_amount ?? 0),
				};
			});
		} finally {
			setIsLoadingMerchants(false);
		}
	}, [accessToken, userDetails]);

	const transferEvalue = useCallback(
		async (params: {
			customer_id: string;
			amount: string;
			remarks: string;
			payment_mode: string;
		}): Promise<TransferResult> => {
			setIsTransferring(true);
			try {
				const payload = {
					...buildBasePayload(userDetails),
					interaction_type_id: MEVALUE_INTERACTION.TRANSFER,
					config_value: "0",
					customer_id: params.customer_id,
					amount: params.amount,
					remarks: params.remarks,
					payment_mode: params.payment_mode,
					clubTransaction: 1,
					latlong: "",
				};
				const data = await fetcher("/api/transaction", {
					token: accessToken,
					body: payload,
				});

				if (data?.response_status_id !== 0) {
					throw new Error(
						data?.message || "Transfer failed. Please try again."
					);
				}

				return {
					tid: String(data.data?.tid ?? ""),
					amount: String(data.data?.amount ?? ""),
					balance: String(data.data?.balance ?? ""),
					customer_id: String(data.data?.customer_id ?? ""),
					message:
						data.message ||
						"Success! Evalue transferred successfully",
				};
			} finally {
				setIsTransferring(false);
			}
		},
		[accessToken, userDetails]
	);

	return {
		fetchHistory,
		fetchMerchantList,
		transferEvalue,
		isLoadingHistory,
		isLoadingMerchants,
		isTransferring,
	};
};
