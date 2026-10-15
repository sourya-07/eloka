package in.eko.eloka.network.internal;

import in.eko.eloka.network.PersonalInfo;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.FieldCipher;
import org.springframework.stereotype.Component;

/** Turns stored users into views (decrypting contact data). */
@Component
class UserMapper {

	private final FieldCipher cipher;

	UserMapper(FieldCipher cipher) {
		this.cipher = cipher;
	}

	UserView toView(AppUser user) {
		return new UserView(user.getId(), user.getOrgId(), user.getUserCode(), user.getUserType(), user.isOrgAdmin(),
				user.getStatus(), user.getName(), cipher.decrypt(user.getMobileEnc()), cipher.decrypt(user.getEmailEnc()),
				user.getDateOfJoining(), user.isDemo(), user.getParentUserId(),
				new PersonalInfo(user.getDob(), user.getGender(), user.getMaritalStatus(), user.getQualification()),
				user.getGstin(), cipher.decrypt(user.getBusinessEmailEnc()), user.getDemoExpiresOn());
	}
}
