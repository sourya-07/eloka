import { BreadcrumbWrapper, PaddingBox, PageTitle } from "@/components/ui";
import { GstinVerification } from "@/screens/products/kyc/gstin/GstinVerification";

// TODO: Confirm role

const GstinPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={{
					"/products/kyc": "KYC Verification Tools",
					"/products/kyc/gstin": "GSTIN Verification",
				}}
				hideHome
			>
				<PageTitle title="GSTIN Verification" />
				<GstinVerification />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

export default GstinPage;
