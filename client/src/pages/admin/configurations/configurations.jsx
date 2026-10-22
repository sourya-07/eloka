import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { Configurations } from "@/screens/Admin";

const ConfigurationsPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={{
					"/admin/configurations": "Configurations",
				}}
			>
				<Configurations />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

ConfigurationsPage.pageMeta = {
	title: "Configurations | Admin",
};

export default ConfigurationsPage;
