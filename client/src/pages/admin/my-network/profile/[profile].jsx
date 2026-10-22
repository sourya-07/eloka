import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { MyNetworkBreadcrumbs } from "@/constants";

import { ProfilePanel } from "@/screens/Admin";

const Profile = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={MyNetworkBreadcrumbs}>
				<ProfilePanel />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

Profile.pageMeta = {
	title: "My Network > Details | Admin",
};

export default Profile;
