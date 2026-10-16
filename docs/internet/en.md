# Web search and links

## Retrieving information

Conversation settings enable **Web search**. Personal conversation, short reactions and computer-clock questions skip search planning. Weather, news, prices, exchange rates and explicit lookup requests let the local model compose a short query. Follow-ups such as “and tomorrow?” use the recent topic rather than an unrelated older discussion.

Weather uses Open-Meteo and asks for the city when unknown. General search uses DuckDuckGo first and Bing RSS as a fallback. The default path requires no API key, subscription, Node.js, separate MCP process or Docker.

Exa is an optional additional fallback, called only after free search fails and only with a saved personal key. No key means no Exa requests. Its integration uses the official MCP Java SDK and only web_search_exa. Public search availability can change; when both free providers are unavailable, the assistant reports that information could not be verified.

Selection removes search-engine pages, unrelated topics and duplicates with tracking parameters. At most three sources survive, with no more than two per host. Query terms are compared with titles and content; resource dictionaries include stop words and aliases such as Russian “dollar” → USD. Up to two pages are read concurrently and rechecked for relevance. Failed page reads retain available search excerpts. Successful research is cached for two minutes; failures are not retained.

The model receives retrieval time and known publication dates. Instructions distinguish old news, stable releases and future plans, and require an honest statement when evidence is insufficient. External content is marked as untrusted data. This improves grounding without guaranteeing every model statement is correct.

## Link policy

Search facts are composed as a short personal reply using the owner's real writing samples and shared profile. Known AI messages are excluded from those samples. Currency data uses ordinary names and symbols instead of ISO-pair notation; significant digits, units and effective dates must survive the retelling. A separate concise research prompt keeps current facts central even when the owner's typical messages are very short.

Conversation settings → **Configure conversation** → **Links in replies**:

- **When the contact asks**: the default. Ordinary weather or news replies do not acquire an unsolicited URL.
- **Whenever search is used**: adds a retrieved source if the model omitted a valid link.
- **Do not send**: removes links from replies using external information.

Link requests support Russian, English, Kazakh and Ukrainian. “Without links” is not treated as a link request. Source URLs are hidden from external model context when unnecessary. A separate output check permits only selected source addresses and removes invented URLs and citation markers. The old unconditional first-result append is removed. Ordinary links in conversations that did not use research are preserved.

## Personal key

Profile → **Settings** → **Web search** → **Optional: Exa fallback search** shows Exa status and whether a key exists. **Get an Exa key** opens the [Exa dashboard](https://dashboard.exa.ai/api-keys). Enter a key and select **Save key**. It applies immediately, independently of other settings; the field clears after saving. **Remove key** disables the optional Exa fallback. Primary free search remains available.

`%LOCALAPPDATA%/Tiho/search.credentials` stores the key encrypted with Windows DPAPI. Exa receives it only in the `x-api-key` header. It is excluded from `settings.json`, API responses and logs. **Test connection** performs one fixed public resource-defined query and does not use conversation content.

The local model processes conversation history on the computer. Search receives a generated query capped at 180 characters; weather receives a city. Full conversations and Telegram credentials are not sent to these services. Existing checks block recognized phone numbers and email addresses. Public-page retrieval retains local-address and redirect protection.

## Contract and extension

- `GET /api/search/connection`: only `configured` and `unchecked`, `ready`, `limited` or `unavailable` state.
- `POST /api/search/connection`: save `apiKey`.
- `DELETE /api/search/connection`: remove the key.
- `POST /api/search/check`: check connectivity.
- `ChatRule.sourceLinks`: `requested`, `always`, `never`; older rules default to `requested`.

All routes use existing local-session and Host/Origin checks. Prompts live in `resources/prompts/ru/research`, regex in `resources/patterns`, stop words in `resources/search/stopwords/<language>.json`, and aliases in `resources/search/aliases.json`. Extend the existing `languages.json` and language-specific files for a new language.

Tests cover MCP initialization and SSE results, rate-limit instructions, fallback and retry delays, key revisions, DPAPI and non-echoing endpoints, ranking and duplicates, citation policies, conversation preferences and UI failures. Browser tests mock API responses; optional Ollama validation uses synthetic conversations.


## Verified rates, news and follow-ups

Ordinary official exchange-rate questions use the Bank of Russia XML service for the current date, retaining the rate's effective date. Nominal units are normalized, so a quote for 100 units becomes a per-unit rate. Supported aliases cover USD, EUR, RUB, KZT, UAH, CNY and GBP, including cross rates. These are official reference rates, not live trading or cash purchase/sale prices; forecasts and bank transactions remain general search tasks. See the [Bank of Russia XML documentation](https://www.cbr.ru/development/SXML/).

Ordinary news uses public RBC and TASS RSS feeds with publication dates. Undated, future and over-36-hour-old items are excluded. Today requests require the current calendar date. Russia and world roundups are separated; other topics filter available headlines and descriptions. If no verified material is available, the application reports this without generating current events or prices from model memory.

Each chat retains its last successful plan for up to ten minutes. Adjacent source/detail requests reuse that verified topic and the short-lived data cache rather than inventing a new query from a generated headline. An intervening topic prevents reuse. A city change after tomorrow retains the forecast date unless explicitly overridden. And euro after a dollar-rate question obtains a new EUR quote.

Live checks run with TIHO_LIVE_INTERNET=true against installed Gemma 3 4B using public synthetic questions; no Telegram messages are sent. backend/build/reports/live-research-conversations.json records answers, queries and sources. USD/EUR values are compared with an independent Bank of Russia XML read; tests also verify source links, publication freshness and forecast dates across follow-ups.
