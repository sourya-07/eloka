import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { MyNetworkBreadcrumbs } from "@/constants";
import { UpdateSellerInfo } from "@/screens/Admin";

/**
 * Update Seller Info page for My Network
 * @returns {JSX.Element} The page component
 */
function UpdateSellerInfoPage() {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={MyNetworkBreadcrumbs}>
				<UpdateSellerInfo />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
}

UpdateSellerInfoPage.pageMeta = {
	title: "My Network > Update Retailer Info",
};

export default UpdateSellerInfoPage;
