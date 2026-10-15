package in.eko.eloka.network.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Named counter (locked while incremented) for human-facing codes such as user codes. */
@Entity
@Table(name = "id_sequence")
class IdSequence {

	@Id
	@Column(length = 40)
	private String name;

	@Column(name = "next_value", nullable = false)
	private long nextValue;

	protected IdSequence() {
	}

	long take() {
		return nextValue++;
	}
}
