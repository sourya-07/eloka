import { PaddingBox, PageTitle } from "@/components/ui";
import { MerchantEvaluePage } from "@/features/merchant-evalue";

const AdminMerchantEvalueRoute = (): JSX.Element => {
	return (
		<PaddingBox>
			<PageTitle title="Merchant E-Value" hideBackIcon />
			<MerchantEvaluePage />
		</PaddingBox>
	);
};

AdminMerchantEvalueRoute.pageMeta = {
	title: "Merchant E-Value",
	isSubPage: false,
};

export default AdminMerchantEvalueRoute;
