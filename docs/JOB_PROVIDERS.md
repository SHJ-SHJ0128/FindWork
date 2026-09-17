# Job Copilot — Provider Evaluation

Status labels: **V1** means selected for the implementation plan; **V1 opt-in** means isolated and disabled unless explicitly enabled; **Later** means documented but not part of the first implementation slice; **No** means do not build a direct integration under current constraints.

| Source | Integration type | Stability / effort / risk | Decision |
| --- | --- | --- | --- |
| LinkedIn alert email | Gmail email ingestion | Stable delivery path; read-only OAuth and bounded link parser implemented; lower anti-bot exposure than scraping | **V1**, daily |
| LinkedIn direct | Browser automation or partner API | No general public discovery API; high account/ToS/anti-bot risk; high maintenance | **V1 opt-in**, stop on challenge; no bypass |
| BOSS Zhipin | Browser automation/third-party scraper or manual URL | No public discovery API found; high anti-bot/ToS risk; China-only relevance | **V1 opt-in**, isolated and user-controlled |
| JobSpy | Third-party scraper | MIT code, but source rights do not follow the library license; supports multiple boards and proxies; high block/maintenance risk | **V1 opt-in**, never use proxy bypass; not a system dependency |
| Indeed alert email | Email ingestion | Medium template maintenance; OAuth/privacy if Gmail; safer than direct scraping | **V1 opt-in**, weekly |
| Indeed direct | Partner API or scraper | Partner access is required for official APIs; direct scraping is restricted | **Later**, partner access only |
| Glassdoor | Partner/legacy API or scraper | Current self-serve API availability unconfirmed; automated access risk high | **No direct V1** |
| Google Jobs | Search UI/aggregator | No supported job retrieval API; scraping UI is brittle | **No direct V1** |
| Greenhouse | Public ATS Job Board API | Public GET endpoint, low anti-bot risk, low/medium adapter effort | **V1**, weekly |
| Lever | Public ATS Postings API | Public JSON postings, low/medium effort and risk | **V1**, weekly |
| Workday | Tenant API or public UI | Tenant-specific credentials/terms; generic UI endpoints unsupported | **Later**, per-company permission/feed only |
| Company career pages | Reviewed JSON-LD/static endpoint | Medium per-domain maintenance; robots/terms vary | **V1** only for a small allowlist |
| Manual URL/pasted JD | Manual import | Lowest platform risk; URL-only fallback handles inaccessible pages | **V1** |
| Gmail | Gmail API read-only OAuth | Stable API; restricted data scope and token handling are the main costs | **V1** for LinkedIn alerts |

## Scheduling

LinkedIn Gmail ingestion runs daily when the application is open. BOSS, JobSpy, browser, ATS, company pages, and other configured sources run weekly. A missed run is visible and can be explicitly replayed; the app does not pretend to run while closed.

## Source rules

- Prefer official APIs, ATS feeds, alert mail, JSON-LD, sitemaps, and user-provided content.
- Identify the client, use modest rate limits, obey robots/terms, and stop on denial.
- Do not design around bypassing anti-bot protections.
- Store original URLs and attribute the source; do not republish more content than the personal workflow needs.
- A provider is allowed to return partial results and warnings.

## Open-source project research

| Project | What was observed | License / reuse decision |
| --- | --- | --- |
| [speedyapply/JobSpy](https://github.com/speedyapply/JobSpy) | Python aggregator for LinkedIn, Indeed, Glassdoor, Google and others; emits a useful job-post schema; README advertises proxy support and reports restrictive board limits. | GitHub shows MIT. Reuse only as an isolated optional adapter after dependency/license review; do not copy proxy/bypass behavior. |
| [feder-cr/AIHawk](https://github.com/feder-cr/AIHawk) | Browser agent/MCP project aimed at undetected browsing; not a domain-specific job provider. | Current repository LICENSE is MIT, but its anti-detect direction conflicts with this project's safety boundary. Do not copy or depend on it. |
| [Buccal/job_Spider](https://github.com/Buccal/job_Spider) | Selenium BOSS scraper storing MongoDB data and generating word clouds; README says the code was last known usable around 2021. | GitHub README identifies MIT. Useful only as historical field ideas; no production code reuse. |
| [Shailja-Jindal/Bidirectional-Job-Resume-Recommender-System](https://github.com/Shailja-Jindal/Bidirectional-Job-Resume-Recommender-System) | Notebook/Streamlit Doc2Vec, TF-IDF and fuzzy-title experiments using sample/Kaggle data. | No license was visible in the repository page checked; treat as **UNAVAILABLE** and do not copy code. Concepts are inspiration only. |
| [browser-use/browser-use](https://github.com/browser-use/browser-use) | Actively maintained general browser agent with Python/TypeScript paths and local-browser support. | GitHub shows MIT. Consider only for a future user-triggered browser adapter; it is not a reason to add a permanent Python service. |
| [imenFerjani/NLP_Job_Skills_match](https://github.com/imenFerjani/NLP_Job_Skills_match) | Small NLP skill-matching experiment with a few commits and no visible license in the checked page. | **UNAVAILABLE** license; do not copy. |

These projects were inspected for architecture and risks, not vendored. Recheck license, activity, and terms immediately before any future dependency decision.

## Provider roadmap

V1 proves the complete pipeline with manual import, Gmail alerts, Greenhouse, Lever, reviewed career pages, and opt-in BOSS/JobSpy/browser adapters. Later work may add approved partner APIs, Workday feeds, wider company allowlists, and application tracking. Direct LinkedIn/BOSS collection remains a best-effort optional capability, never the sole source of truth.
