import { PaddingBox } from "@/components/ui";
import { Home } from "@/screens";

const AgentViewHomePage = () => {
	return (
		<PaddingBox>
			<Home />
		</PaddingBox>
	);
};

AgentViewHomePage.pageMeta = {
	title: "Agent Homepage | Admin",
};

export default AgentViewHomePage;
