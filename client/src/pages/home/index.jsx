import { PaddingBox } from "@/components/ui";
import { useFeatureFlag } from "@/hooks";
import { Home } from "@/screens";
import { Dashboard } from "@/screens/Admin";

const HomePage = () => {
	const [showAdminDashboard] = useFeatureFlag(
		"ADMIN_DASHBOARD_FOR_SUBNETWORK"
	);

	return (
		<PaddingBox>{showAdminDashboard ? <Dashboard /> : <Home />}</PaddingBox>
	);
};

HomePage.pageMeta = {
	title: "Home",
};

export default HomePage;
