import { PaddingBox } from "@/components/ui";
import { Dashboard } from "@/screens/Admin";

const DashboardPage = () => {
	return (
		<PaddingBox>
			<Dashboard />
		</PaddingBox>
	);
};

DashboardPage.pageMeta = {
	title: "Dashboard | Admin",
};

export default DashboardPage;
