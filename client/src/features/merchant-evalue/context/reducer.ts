import { MerchantEvalueAction, MerchantEvalueState } from "./types";

export const merchantEvalueReducer = (
	state: MerchantEvalueState,
	action: MerchantEvalueAction
): MerchantEvalueState => {
	switch (action.type) {
		case "SET_STEP":
			return { ...state, step: action.payload };
		case "SET_HISTORY":
			return { ...state, history: action.payload };
		case "SET_MERCHANTS":
			return { ...state, merchants: action.payload };
		case "SELECT_MERCHANT":
			return { ...state, selectedMerchant: action.payload };
		case "SET_TRANSFER_RESULT":
			return { ...state, transferResult: action.payload };
		case "SET_ERROR":
			return { ...state, errorMessage: action.payload };
		case "CLEAR_ERROR":
			return { ...state, errorMessage: null };
		default:
			return state;
	}
};
