import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { MyNetworkBreadcrumbs } from "@/constants";
import { ChangeRole } from "@/screens/Admin";

const ChangeRolePage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={MyNetworkBreadcrumbs}>
				<ChangeRole />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

ChangeRolePage.pageMeta = {
	title: "My Network > Change Role | Admin",
	isFixedBottomAppBar: true,
};

export default ChangeRolePage;
