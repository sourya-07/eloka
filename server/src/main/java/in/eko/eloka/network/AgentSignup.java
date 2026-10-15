package in.eko.eloka.network;

/**
 * One agent to onboard (a form row or a spreadsheet row).
 *
 * @param distributorMobile distributor to place a retailer under (admin only), or null
 * @param demoValidityDays demo accounts only: days until it expires, or null for no expiry
 */
public record AgentSignup(String name, String mobile, String distributorMobile, boolean demo, Integer demoValidityDays) {
}
