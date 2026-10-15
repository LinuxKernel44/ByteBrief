# Security policy

Please report vulnerabilities privately through GitHub's "Report a vulnerability" (Security tab) rather than a
public issue. Include the version, a description and, if possible, a minimal reproduction.

Areas of particular interest: URL scheme handling, the HTTP fetcher, feed/OPML/HTML parsing, prompt-injection
defences, the model downloader's integrity checks, and the Keystore-backed token store. The design is described
in `docs/ARCHITECTURE.md` ("Security model").
