import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { MyNetworkBreadcrumbs } from "@/constants";
import { Network } from "@/screens/Admin";

const MyNetwork = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper breadcrumbsData={MyNetworkBreadcrumbs}>
				<Network />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

MyNetwork.pageMeta = {
	title: "My Network | Admin",
};

export default MyNetwork;
