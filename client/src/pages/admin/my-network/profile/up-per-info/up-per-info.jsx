import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { MyNetworkBreadcrumbs } from "@/constants";
import { UpdatePersonalInfo } from "@/screens/Admin";

/**
 *
 */
function UpdatePersonalInfoPage() {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={MyNetworkBreadcrumbs}>
				<UpdatePersonalInfo />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
}

UpdatePersonalInfoPage.pageMeta = {
	title: "My Network > Update Personal Info | Admin",
	isFixedBottomAppBar: true,
};

export default UpdatePersonalInfoPage;
