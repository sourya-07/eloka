-- P8 fix: a rejection keeps both the message for the applicant and the internal note.
ALTER TABLE review_action ADD COLUMN applicant_message VARCHAR(1000) NULL AFTER note;
