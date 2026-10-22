import { PaddingBox } from "@/components/ui";
import { History } from "@/screens";

const NetworkStatementPage = () => {
	return (
		<PaddingBox>
			<History forNetwork />
		</PaddingBox>
	);
};

// If the "subPage" property is set, then this page will hide the top app header in mobile view.
// Pages can show their own header bar with back button.
NetworkStatementPage.pageMeta = {
	title: "Network's Transaction History",
	isSubPage: true,
};

export default NetworkStatementPage;
