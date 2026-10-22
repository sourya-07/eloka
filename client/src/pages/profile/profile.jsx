import { PaddingBox } from "@/components/ui";
import { Profile } from "@/screens";

const ProfilePage = () => {
	return (
		<PaddingBox>
			<Profile />
		</PaddingBox>
	);
};

ProfilePage.pageMeta = {
	title: "My Profile",
};

export default ProfilePage;
