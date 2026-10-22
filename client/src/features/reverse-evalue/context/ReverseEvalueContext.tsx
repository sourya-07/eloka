import React, { createContext, useContext, useMemo, useReducer } from "react";
import { reverseEvalueReducer } from "./reducer";
import { initialState, ReverseEvalueAction, ReverseEvalueState } from "./types";

interface ReverseEvalueContextValue {
	state: ReverseEvalueState;
	dispatch: React.Dispatch<ReverseEvalueAction>;
}

const ReverseEvalueContext = createContext<ReverseEvalueContextValue>(null!);

export const ReverseEvalueProvider: React.FC<{
	children: React.ReactNode;
}> = ({ children }) => {
	const [state, dispatch] = useReducer(reverseEvalueReducer, initialState);
	const value = useMemo(() => ({ state, dispatch }), [state]);
	return (
		<ReverseEvalueContext.Provider value={value}>
			{children}
		</ReverseEvalueContext.Provider>
	);
};

export const useReverseEvalue = (): ReverseEvalueContextValue => {
	const ctx = useContext(ReverseEvalueContext);
	if (!ctx) {
		throw new Error(
			"useReverseEvalue must be used inside <ReverseEvalueProvider>"
		);
	}
	return ctx;
};
