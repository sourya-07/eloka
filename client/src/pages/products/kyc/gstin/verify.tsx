import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { GstinVerifyForm } from "@/screens/products/kyc/gstin/GstinVerifyForm";

const GstinVerifyPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={{
					"/products/kyc": "KYC Verification Tools",
					"/products/kyc/gstin": "GSTIN Verification",
					"/products/kyc/gstin/verify": "Verify GSTIN",
				}}
				hideHome
			>
				<GstinVerifyForm />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

export default GstinVerifyPage;
