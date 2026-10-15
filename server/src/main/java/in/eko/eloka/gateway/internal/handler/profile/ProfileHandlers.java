package in.eko.eloka.gateway.internal.handler.profile;

import java.util.List;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsParams;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.handler.EpsValues;
import in.eko.eloka.network.NetworkCommands;
import in.eko.eloka.network.PersonalInfo;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import org.springframework.stereotype.Component;

/** The caller's own profile: edit personal / shop details (401) and read the GSTIN (373). */
final class ProfileHandlers {

	private ProfileHandlers() {
	}

	/** 401 with {@code section} = personal_detail | shop_detail. The client then refreshes the profile. */
	@Component
	static class UpdateOwnProfile implements EpsHandler {

		private final NetworkCommands commands;

		UpdateOwnProfile(NetworkCommands commands) {
			this.commands = commands;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(401));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			EpsParams p = call.params();
			switch (p.requireString("section")) {
				case "personal_detail" -> commands.updatePersonal(caller, null, null,
						new PersonalInfo(p.string("dob").map(v -> EpsValues.date("dob", v)).orElse(null),
								p.string("gender").orElse(null), p.string("marital_status").orElse(null),
								p.string("qualification").orElse(null)));
				case "shop_detail" -> {
					String pincode = p.string("pincode").orElse(null);
					if (pincode != null && !pincode.matches("\\d{6}")) {
						throw ValidationException.field("pincode", "Enter a 6-digit pincode");
					}
					commands.updateShop(caller, null, new ShopView(p.string("shop_name").orElse(null),
							p.string("shop_type").orElse(null), p.string("shop_address").orElse(null), null,
							p.string("city").orElse(null), p.string("shop_address_state").orElse(null), pincode, null, null,
							null, null));
				}
				default -> throw ValidationException.field("section", "Unknown profile section");
			}
			return EpsEnvelope.ok().message("Profile updated").build();
		}
	}

	/** 373: account summary (GSTIN). */
	@Component
	static class AccountSummary implements EpsHandler {

		private final UserDirectory users;

		AccountSummary(UserDirectory users) {
			this.users = users;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(373));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			String gstin = users.getById(call.caller().orgId(), call.caller().userId()).gstin();
			return EpsEnvelope.ok().data(java.util.Collections.singletonMap("gstin", gstin)).build();
		}
	}
}
