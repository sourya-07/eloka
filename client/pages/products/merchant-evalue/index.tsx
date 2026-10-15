import { PaddingBox, PageTitle } from "components";
import { MerchantEvaluePage } from "features/merchant-evalue";

const MerchantEvalueRoute = (): JSX.Element => {
	return (
		<PaddingBox>
			<PageTitle title="Merchant E-Value" hideBackIcon />
			<MerchantEvaluePage />
		</PaddingBox>
	);
};

MerchantEvalueRoute.pageMeta = {
	title: "Merchant E-Value",
	isSubPage: false,
};

export default MerchantEvalueRoute;
