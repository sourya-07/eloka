// filepath: /Users/abhi/DEV/eko_github/wlc-webapp/pages/products/kyc/index.tsx
import { PaddingBox, PageTitle } from "@/components/ui";
import { KycVerificationTools } from "@/screens/products/kyc/KycVerificationTools";

const KycPage = (): JSX.Element => {
	return (
		<PaddingBox>
			<PageTitle title="KYC & Verification" isBeta hideBackIcon />
			<KycVerificationTools />
		</PaddingBox>
	);
};

export default KycPage;
