-- P8 fixes: a correction for several steps and its full message must fit.
-- review_action.target_ref holds the reopened step keys joined by commas (all 12 reopenable keys = 168 chars).
ALTER TABLE review_action MODIFY target_ref VARCHAR(255) NULL;
-- The applicant sees the reviewer's message on each reopened step; same limit as correction_request.message.
ALTER TABLE onboarding_step MODIFY reviewer_message VARCHAR(1000) NULL;
