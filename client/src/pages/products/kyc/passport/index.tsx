import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { PassportForm } from "@/screens/products/kyc/passport/PassportForm";

/**
 * Page for verifying Indian passport details
 */
const PassportVerifyPage = (): JSX.Element => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={{
					"/products/kyc": "KYC Verification Tools",
					"/products/kyc/passport": "Passport Verification",
				}}
				hideHome
			>
				<PassportForm />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

export default PassportVerifyPage;
