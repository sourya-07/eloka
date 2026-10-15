package in.eko.eloka.pricing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catalog entry (reference data, maintained through migrations). */
@Entity
@Table(name = "product")
class Product {

	@Id
	private int id;

	@Column(nullable = false, length = 80)
	private String label;

	@Column(length = 40)
	private String icon;

	@Column(nullable = false)
	private int behavior;

	@Column(nullable = false)
	private boolean visible;

	@Column(length = 40)
	private String category;

	@Column(length = 200)
	private String description;

	@Column(name = "tx_typeid", nullable = false)
	private int txTypeId;

	@Column(name = "history_label", length = 80)
	private String historyLabel;

	@Column(name = "group_interaction_ids", length = 200)
	private String groupInteractionIds;

	@Column(name = "important", nullable = false)
	private boolean important;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	protected Product() {
	}

	int getId() { return id; }
	String getLabel() { return label; }
	String getIcon() { return icon; }
	int getBehavior() { return behavior; }
	boolean isVisible() { return visible; }
	String getCategory() { return category; }
	String getDescription() { return description; }
	int getTxTypeId() { return txTypeId; }
	String getHistoryLabel() { return historyLabel; }
	String getGroupInteractionIds() { return groupInteractionIds; }
	boolean isImportant() { return important; }
	int getSortOrder() { return sortOrder; }
}
