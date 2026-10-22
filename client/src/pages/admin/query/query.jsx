import { BreadcrumbWrapper, PaddingBox } from "@/components/ui";
import { QueryCenter } from "@/screens/Admin";

const QueryPage = () => {
	return (
		<PaddingBox>
			<BreadcrumbWrapper
				breadcrumbsData={{
					"/admin/query": "Query Center",
				}}
			>
				<QueryCenter />
			</BreadcrumbWrapper>
		</PaddingBox>
	);
};

QueryPage.pageMeta = {
	title: "Query Center | Admin",
};

export default QueryPage;
