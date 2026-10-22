import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import {
	PricingCommissionBreadcrumbs,
	product_pricing_categories,
} from "@/constants";
import { ConfigPageCard } from "@/screens/Admin";
import { DownloadPricing } from "@/screens/Admin/PricingCommission/DownloadPricing";

/**
 * Show the Pricing & Commission page: grid of products to set pricing/commissions for.
 */
const PricingPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={PricingCommissionBreadcrumbs}>
				<ConfigPageCard
					heading="Pricing & Commissions"
					configCategories={product_pricing_categories}
					HeaderTool={<DownloadPricing />}
				/>
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

PricingPage.pageMeta = {
	title: "PricingPage & Commission | Admin",
};

export default PricingPage;
