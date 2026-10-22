import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { MyNetworkBreadcrumbs } from "@/constants";
import { UpdateSellerInfo } from "@/screens/Admin";

/**
 *
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
	title: "My Network > Update Retailer Info | Admin",
};

export default UpdateSellerInfoPage;
