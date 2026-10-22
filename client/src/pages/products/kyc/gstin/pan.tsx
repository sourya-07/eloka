import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { GstinPanForm } from "@/screens/products/kyc/gstin/GstinPanForm";

const GstinPanPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={{
					"/products/kyc": "KYC Verification Tools",
					"/products/kyc/gstin": "GSTIN Verification",
					"/products/kyc/gstin/pan": "PAN Lookup",
				}}
				hideHome
			>
				<GstinPanForm />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

export default GstinPanPage;
