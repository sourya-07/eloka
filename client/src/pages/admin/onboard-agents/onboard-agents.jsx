import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { OnboardAgents } from "@/screens/Admin";

const OnboardAgentsPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={{
					"/admin/onboard-agents": "Onboard Agents",
				}}
			>
				<OnboardAgents />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

OnboardAgentsPage.pageMeta = {
	title: "Onboard Agents | Admin",
	isFixedBottomAppBar: true,
};

export default OnboardAgentsPage;
