package in.eko.eloka.agentonboarding;

/** Business step data that has no other home (shop name and address go to the network module). */
public record BusinessDetails(String companyType, String authorizedSignatory, String contactPersonMobile,
		String alternateMobile) {
}
