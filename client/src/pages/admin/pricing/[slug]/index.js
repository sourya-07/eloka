import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { PricingCommissionBreadcrumbs } from "@/constants";
import { useRouter } from "next/router";
import { PricingForm } from "@/screens/Admin";

const PricingFormPage = () => {
	const router = useRouter();
	const { slug } = router.query;

	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={PricingCommissionBreadcrumbs}
				slug={slug}
			>
				<PricingForm slug={slug} />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

PricingFormPage.pageMeta = {
	title: `Configure Pricing & Commissions`,
	isFixedBottomAppBar: true,
};

export default PricingFormPage;
