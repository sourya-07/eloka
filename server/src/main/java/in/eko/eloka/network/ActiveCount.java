package in.eko.eloka.network;

/** Users of one type: how many are active out of how many exist. */
public record ActiveCount(long active, long total) {
}
