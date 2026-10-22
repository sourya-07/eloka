import { BreadcrumbWrapper, PaddingBox, PageTitle } from "@/components/ui";
import { PanVerification } from "@/screens/products/kyc/pan/PanVerification";

const PanPage = (): JSX.Element => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={{
					"/products/kyc": "KYC Verification Tools",
					"/products/kyc/pan": "PAN Verification",
				}}
				hideHome
			>
				<PageTitle title="PAN Verification" />
				<PanVerification />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

export default PanPage;
