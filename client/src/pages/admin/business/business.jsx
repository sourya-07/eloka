import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { business_setting_categories } from "@/constants";
import { BusinessSettingsBreadcrumbs } from "@/constants/BreadcrumbsData";
import { ConfigPageCard } from "@/screens/Admin";

/**
 * Show the Pricing & Commission page: grid of products to set pricing/commissions for.
 */
const BusinessPage = () => {
	// return null;

	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={BusinessSettingsBreadcrumbs}>
				<ConfigPageCard
					heading="Business Settings"
					configCategories={business_setting_categories}
				/>
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

BusinessPage.pageMeta = {
	title: "Business Settings",
};

export default BusinessPage;
