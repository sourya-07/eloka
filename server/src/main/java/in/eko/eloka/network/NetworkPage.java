package in.eko.eloka.network;

import java.util.List;

/** One page of network users plus the total that match. */
public record NetworkPage(List<UserView> users, long total) {
}
