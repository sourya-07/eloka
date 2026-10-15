-- Distributor hierarchy: each user may sit under a parent; hierarchy_path ("/<ancestor ids>/<self>/") makes
-- "everyone below X" a prefix query.
ALTER TABLE app_user
    ADD COLUMN parent_user_id BINARY(16)   NULL,
    ADD COLUMN hierarchy_path VARCHAR(800) CHARACTER SET ascii COLLATE ascii_bin NULL, -- ids and slashes only
    ADD INDEX app_user_hierarchy_idx (org_id, hierarchy_path),
    ADD INDEX app_user_parent_idx (parent_user_id);
