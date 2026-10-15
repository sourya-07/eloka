/**
 * Helpers that build responses in the EPS "interaction" envelope the frontend expects:
 * `{ status, response_status_id, response_type_id, message, data, ... }`.
 * `status: 0` / `response_status_id: 0` means success.
 */

/**
 * Successful EPS response.
 * @param {object} [data] - Payload placed under `data`
 * @param {object} [extra] - Extra top-level fields (e.g. `response_type_id`, `param_attributes`)
 * @returns {object} Envelope
 */
export const ok = (data = {}, extra = {}) => ({
	status: 0,
	response_status_id: 0,
	response_type_id: 0,
	message: "Success",
	data,
	...extra,
});

/**
 * Failed EPS response.
 * @param {string} message - Human-readable error shown by the UI
 * @param {object} [extra] - Extra top-level fields (e.g. `response_type_id`, `invalid_params`)
 * @returns {object} Envelope
 */
export const fail = (message, extra = {}) => ({
	status: 1,
	response_status_id: 1,
	response_type_id: -1,
	message,
	data: {},
	...extra,
});

/**
 * Response for dropdown lookups (shop types, states, banks...).
 * @param {Array<object>} listElements - `[{ value, label, ... }]`
 * @returns {object} Envelope with `param_attributes.list_elements`
 */
export const list = (listElements) =>
	ok({}, { param_attributes: { list_elements: listElements } });
