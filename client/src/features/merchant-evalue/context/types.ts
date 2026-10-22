import { MEVALUE_STEP } from "../constants";

export type MerchantEvalueStep =
	(typeof MEVALUE_STEP)[keyof typeof MEVALUE_STEP];

/** One row from the history API response */
export interface EvalueHistoryRecord {
	tx_status: number;
	shop: string;
	txstatus_desc: string;
	csp_code: string;
	created_at: string;
	approval_tid: number;
	tid: number;
	value_to_be_transferred: number;
	updated_at: string;
	approved_at: string;
	cash_collected: number;
	value_transferred: number;
	payment_mode: string;
	csp_name: string;
	approved_by: string;
	outstanding_amount: number;
	customer_id: string;
}

/** One merchant from the merchant-list API */
export interface MerchantListItem {
	label: string;
	value: string;
	balance: number;
	shop: string;
	csp_code: string;
	cce_name: string;
	outstanding_limit: number;
	outstanding_amount: number;
}

/** Result of a successful transfer */
export interface TransferResult {
	tid: string;
	amount: string;
	balance: string;
	customer_id: string;
	message: string;
}

export interface MerchantEvalueState {
	step: MerchantEvalueStep;
	history: EvalueHistoryRecord[];
	merchants: MerchantListItem[];
	selectedMerchant: MerchantListItem | null;
	transferResult: TransferResult | null;
	errorMessage: string | null;
}

export type MerchantEvalueAction =
	| { type: "SET_STEP"; payload: MerchantEvalueStep }
	| { type: "SET_HISTORY"; payload: EvalueHistoryRecord[] }
	| { type: "SET_MERCHANTS"; payload: MerchantListItem[] }
	| { type: "SELECT_MERCHANT"; payload: MerchantListItem | null }
	| { type: "SET_TRANSFER_RESULT"; payload: TransferResult }
	| { type: "SET_ERROR"; payload: string }
	| { type: "CLEAR_ERROR" };

export const initialMerchantEvalueState: MerchantEvalueState = {
	step: "LOADING_HISTORY",
	history: [],
	merchants: [],
	selectedMerchant: null,
	transferResult: null,
	errorMessage: null,
};
