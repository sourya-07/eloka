import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { MyNetworkBreadcrumbs } from "@/constants";
import { PreviewSellerInfo } from "@/screens/Admin/UpdateSellerInfo";

/**
 *
 */
function PreviewSellerInfoPage() {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={MyNetworkBreadcrumbs}>
				<PreviewSellerInfo />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
}

PreviewSellerInfoPage.pageMeta = {
	title: "My Network > Preview Retailer Info | Admin",
};

export default PreviewSellerInfoPage;
