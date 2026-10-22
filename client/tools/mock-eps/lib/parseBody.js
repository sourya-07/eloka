/**
 * Turn a raw request body into one flat object of fields, whatever the encoding:
 * JSON, URL-encoded, or multipart (the upload proxy sends a URL-encoded `form-data`
 * field plus files; file contents are ignored).
 */

/**
 * @param {Buffer} raw - Raw request body
 * @param {string} [contentType] - Request Content-Type header
 * @returns {object} Parsed fields
 */
export const parseBody = (raw, contentType = "") => {
	if (!raw?.length) return {};
	const text = raw.toString("latin1");

	if (contentType.includes("application/json")) {
		try {
			return JSON.parse(raw.toString("utf8"));
		} catch {
			return {};
		}
	}

	if (contentType.includes("multipart/form-data")) {
		return parseMultipart(text, contentType);
	}

	if (contentType.includes("application/x-www-form-urlencoded")) {
		return Object.fromEntries(new URLSearchParams(text));
	}

	// Unknown type: try JSON, then URL-encoded.
	try {
		return JSON.parse(raw.toString("utf8"));
	} catch {
		return Object.fromEntries(new URLSearchParams(text));
	}
};

/**
 * @param {string} text - Body decoded as latin1
 * @param {string} contentType - Contains the boundary
 * @returns {object} Text fields; a `form-data` field is expanded into its URL-encoded params
 */
const parseMultipart = (text, contentType) => {
	const boundary = contentType.match(/boundary=(?:"([^"]+)"|([^;]+))/i);
	if (!boundary) return {};
	const fields = {};

	for (const part of text.split(`--${boundary[1] ?? boundary[2]}`)) {
		const [rawHeaders, ...rest] = part.split("\r\n\r\n");
		const name = rawHeaders?.match(/name="([^"]+)"/)?.[1];
		if (!name || /filename="/.test(rawHeaders)) continue;
		const value = rest.join("\r\n\r\n").replace(/\r\n$/, "");

		if (name === "form-data") {
			Object.assign(
				fields,
				Object.fromEntries(new URLSearchParams(value))
			);
		} else {
			fields[name] = value;
		}
	}
	return fields;
};
