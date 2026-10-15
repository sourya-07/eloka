export type ActiveTab = "reversible" | "history";
export type ReverseType = "partial" | "full";
export type ReverseEvalueView =
	| "list"
	| "form"
	| "submitting"
	| "success"
	| "failure";

export interface ReversibleRecord {
	tx_status: number;
	csp_code: string;
	csp_name: string;
	customer_id: string;
	created_at: string;
	tid: number;
	reference_tid: number;
	value_transferred: number;
	reverted_amount: number;
	reversible_amount: number;
	agent_balance: number;
	payment_mode: string;
	approved_by: string;
	comment: string;
}

export interface ReverseResult {
	tid: string;
	amount: string;
	requestedAmount: string;
	saving_balance: string;
	csp_balance: string;
	agent_name: string;
	user_code: string;
	transaction_time: string;
	message: string;
}

export interface ReverseEvalueState {
	view: ReverseEvalueView;
	activeTab: ActiveTab;
	reversibleRecords: ReversibleRecord[];
	historyRecords: ReversibleRecord[];
	selectedRecord: ReversibleRecord | null;
	reverseType: ReverseType | null;
	reverseResult: ReverseResult | null;
	errorMessage: string | null;
}

export type ReverseEvalueAction =
	| { type: "SET_VIEW"; payload: ReverseEvalueView }
	| { type: "SET_TAB"; payload: ActiveTab }
	| { type: "SET_REVERSIBLE"; payload: ReversibleRecord[] }
	| { type: "SET_HISTORY"; payload: ReversibleRecord[] }
	| {
			type: "OPEN_FORM";
			payload: { record: ReversibleRecord; reverseType: ReverseType };
	  }
	| { type: "SET_RESULT"; payload: ReverseResult }
	| { type: "SET_ERROR"; payload: string }
	| { type: "CLEAR_ERROR" };

export const initialState: ReverseEvalueState = {
	view: "list",
	activeTab: "reversible",
	reversibleRecords: [],
	historyRecords: [],
	selectedRecord: null,
	reverseType: null,
	reverseResult: null,
	errorMessage: null,
};
