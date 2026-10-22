import React, { createContext, useContext, useMemo, useReducer } from "react";
import { merchantEvalueReducer } from "./reducer";
import {
	initialMerchantEvalueState,
	MerchantEvalueAction,
	MerchantEvalueState,
} from "./types";

interface MerchantEvalueContextValue {
	state: MerchantEvalueState;
	dispatch: React.Dispatch<MerchantEvalueAction>;
}

const MerchantEvalueContext = createContext<MerchantEvalueContextValue>(null!);

export const MerchantEvalueProvider: React.FC<{
	children: React.ReactNode;
}> = ({ children }) => {
	const [state, dispatch] = useReducer(
		merchantEvalueReducer,
		initialMerchantEvalueState
	);
	const value = useMemo(() => ({ state, dispatch }), [state]);

	return (
		<MerchantEvalueContext.Provider value={value}>
			{children}
		</MerchantEvalueContext.Provider>
	);
};

export const useMerchantEvalue = (): MerchantEvalueContextValue => {
	const ctx = useContext(MerchantEvalueContext);
	if (!ctx) {
		throw new Error(
			"useMerchantEvalue must be used inside <MerchantEvalueProvider>"
		);
	}
	return ctx;
};
