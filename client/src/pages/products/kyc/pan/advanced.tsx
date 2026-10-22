import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { PanAdvancedForm } from "@/screens/products/kyc/pan/PanAdvancedForm";

const PanAdvancedPage = (): JSX.Element => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={{
					"/products/kyc": "KYC Verification Tools",
					"/products/kyc/pan": "PAN Verification",
					"/products/kyc/pan/advanced": "PAN Advanced",
				}}
				hideHome
			>
				<PanAdvancedForm />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

export default PanAdvancedPage;
