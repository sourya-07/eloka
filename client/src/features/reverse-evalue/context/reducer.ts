import { ReverseEvalueAction, ReverseEvalueState } from "./types";

export const reverseEvalueReducer = (
	state: ReverseEvalueState,
	action: ReverseEvalueAction
): ReverseEvalueState => {
	switch (action.type) {
		case "SET_VIEW":
			return { ...state, view: action.payload };
		case "SET_TAB":
			return { ...state, activeTab: action.payload };
		case "SET_REVERSIBLE":
			return { ...state, reversibleRecords: action.payload };
		case "SET_HISTORY":
			return { ...state, historyRecords: action.payload };
		case "OPEN_FORM":
			return {
				...state,
				selectedRecord: action.payload.record,
				reverseType: action.payload.reverseType,
				errorMessage: null,
				view: "form",
			};
		case "SET_RESULT":
			return { ...state, reverseResult: action.payload, view: "success" };
		case "SET_ERROR":
			return { ...state, errorMessage: action.payload };
		case "CLEAR_ERROR":
			return { ...state, errorMessage: null };
		default:
			return state;
	}
};
