import { PaddingBox, PageTitle } from "@/components/ui";
import {
	ReverseEvalueProvider,
	ReverseEvaluePage,
} from "@/features/reverse-evalue";

const ReverseEvalueRoute = (): JSX.Element => {
	return (
		<PaddingBox>
			<PageTitle title="Reverse E-Value" hideBackIcon />
			<ReverseEvalueProvider>
				<ReverseEvaluePage />
			</ReverseEvalueProvider>
		</PaddingBox>
	);
};

ReverseEvalueRoute.pageMeta = {
	title: "Reverse E-Value",
	isSubPage: false,
};

export default ReverseEvalueRoute;
