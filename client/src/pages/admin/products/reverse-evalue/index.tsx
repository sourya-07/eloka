import { PaddingBox, PageTitle } from "@/components/ui";
import {
	ReverseEvalueProvider,
	ReverseEvaluePage,
} from "@/features/reverse-evalue";

const AdminReverseEvalueRoute = (): JSX.Element => {
	return (
		<PaddingBox>
			<PageTitle title="Reverse E-Value" hideBackIcon />
			<ReverseEvalueProvider>
				<ReverseEvaluePage />
			</ReverseEvalueProvider>
		</PaddingBox>
	);
};

AdminReverseEvalueRoute.pageMeta = {
	title: "Reverse E-Value",
	isSubPage: false,
};

export default AdminReverseEvalueRoute;
