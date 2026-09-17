# Job Copilot — Security and Privacy

## Threat boundary

V1 is a local personal application bound to loopback. It has no login UI and is not a LAN/cloud service. This reduces exposure but does not make resume, Gmail, API keys, or browser sessions harmless.

## Secrets

- Keep `DEEPSEEK_API_KEY`, `GMAIL_CLIENT_SECRET`, OAuth refresh tokens, database credentials, and browser profile paths out of Git.
- Commit `.env.example`, never `.env`.
- Redact secrets from logs and collection error payloads.
- Rotate keys if they appear in terminal output, screenshots, or Git history.

## DeepSeek privacy

Only send the minimum redacted resume text and job text needed for extraction/explanation. Remove name, phone, email, address, identifiers, and unrelated personal data. Store request metadata (hash, model, timestamp, status), not the full request/response by default. A failed AI call must not erase the deterministic match.

## Gmail

Use Gmail OAuth with read-only access. Restrict searches to LinkedIn alert messages (and later explicitly enabled alert senders/labels). Parse links, titles, companies, locations, and short snippets. Do not download unrelated mail. Store tokens in a local application-data directory with owner-only permissions. Delete raw email content after 30 days.

## Browser automation

Browser/JobSpy providers are opt-in and user-triggered or scheduled only while the app is open. They must not solve CAPTCHA, evade anti-bot controls, rotate proxies to bypass blocking, or store passwords/cookies in the application. Reuse a user-controlled browser profile only when the user explicitly enables it, and stop on an access challenge.

## HTTP and browser boundary

- Bind Spring and Vite to localhost by default.
- Allow only the frontend origin in CORS.
- Validate URL schemes (`https` or an explicitly permitted local scheme) before fetching.
- Apply request size limits to resumes and pasted job descriptions.
- Escape rendered job descriptions and email HTML; treat all source content as untrusted.

## Retention and deletion

- Delete raw job payloads and raw email bodies after 30 days.
- Keep structured job metadata, fingerprints, match evidence, and collection status for useful history.
- Provide a manual delete operation for a source, job, resume, or all local data.
- Keep the active original resume local until explicit deletion.

## Failure and audit signals

Record provider, adapter version, status, error code, retry count, and timestamps. Never log OAuth tokens, API keys, cookies, full resumes, or full email bodies. Mark data as `UNAVAILABLE`, `TIMEOUT`, or `NEEDS_REVIEW` rather than converting failures into negative job matches.
