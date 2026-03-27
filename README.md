## Travel AI Chat

Built primarily to help you find and manage hotel booking effortlessly. Powered by Trivago MCP Server and xAI Grok (swappable with other models with OpenAI-compatible API).

### Stack

- **Backend**: Spring Boot 3.5.3, Spring AI 1.1.4, Java 21
- **Frontend**: React 19, Vite 8, TypeScript, Tailwind CSS, shadcn/ui
- **LLM**: xAI Grok via OpenAI-compatible API
- **MCP**: Trivago MCP server (Streamable HTTP transport, NOT SSE)
- **Database**: PostgreSQL (Docker for dev, hosted for prod)
- **Auth**: Google OAuth2 (session-based, hashed user IDs, no PII stored)

### Architecture

- Trivago MCP server uses **Streamable HTTP** (JSON-RPC over POST) — configured via `spring.ai.mcp.client.streamable-http` in `application.yml`
- Hotel data fetched directly via `McpSyncClient` for real URLs, images, and prices
- Grok analyzes MCP results and provides personalized insights based on user preferences
- Preferences auto-extracted from chat messages by LLM
- Search history persisted and shown as clickable chips

### Chat Flow

1. User sends message
2. LLM extracts search params (city, dates, guests, landmark)
3. `McpSyncClient` calls Trivago tools directly (search-suggestions → accommodation-search, or radius-search for landmarks)
4. Hotel summary (names, prices, ratings, amenities) fed to Grok
5. Grok returns ranked hotel IDs + markdown commentary based on user preferences
6. Backend reorders accommodations to match Grok's ranking
7. Frontend renders Grok's commentary + reordered hotel cards with real images and booking links

### Running

```bash
npm run dev          # starts Docker PostgreSQL + Vite + Spring Boot
npm run dev:stop     # stops PostgreSQL container
```

Requires `.env` file at project root:

```
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
XAI_API_KEY=...
```

### Hacks / Interesting Workarounds

**Why not let Grok call MCP tools directly?**
Spring AI's `ToolCallback` integration lets the LLM invoke MCP tools as part of its response. We tried this first — it works, but Grok (and LLMs in general) filters out critical data like `accommodation_url`, `main_image`, and raw prices from the tool results. The AI "summarizes" the hotel data and fabricates pretty-looking Trivago URLs that 404. The fix: call MCP tools directly via `McpSyncClient`, keep the raw data for the frontend, and only feed a text summary (names, prices, ratings) to Grok for analysis.

**Trivago MCP uses Streamable HTTP, not SSE**
Every Spring AI MCP tutorial uses SSE transport. Trivago's MCP server doesn't support SSE — it uses Streamable HTTP (JSON-RPC over POST). We discovered this by curling the endpoint directly after `HttpClientSseClientTransport` kept timing out. The fix: configure `spring.ai.mcp.client.streamable-http` instead of `spring.ai.mcp.client.sse` in `application.yml`.

**`structuredContent()` vs `TextContent` in MCP Java SDK**
`McpSchema.CallToolResult` has two ways to access data. `structuredContent()` returns an already-parsed `Map` — clean and usable. `TextContent` returns a Go-style `map[]` string (not JSON), which is unparseable. Always prefer `structuredContent()` and fall back to text only if needed.

**LLMs return past dates**
When users say "find hotels in Paris next week", Grok sometimes returns dates from 2023 (its training data). Trivago's MCP rejects past dates with a validation error. Fix: inject today's date into the extraction prompt and validate that the returned arrival date is in the future, falling back to tomorrow if not.

**Two `ChatClient` instances from one `Builder`**
`ChatClient.Builder` is mutable. We need two clients (one for extraction, one for Grok chat) — calling `builder.build()` twice works, but the order matters. The extraction client is built first (no system prompt baked in), then the chat client.

**`FetchType.EAGER` on `User.history`**
JPA's default `LAZY` fetch on `@OneToMany` causes `LazyInitializationException` when accessing `user.getHistory()` outside a transaction. Using `EAGER` is generally discouraged for performance, but with small history lists (capped at ~50 entries per user), it's the pragmatic choice over adding `@Transactional` everywhere.

**`@JsonIgnore` to break circular serialization**
`User` has `List<SearchHistory>`, and `SearchHistory` has a back-reference to `User`. Without `@JsonIgnore` on `SearchHistory.user`, Jackson enters infinite recursion when serializing the history endpoint. This is a standard JPA/Jackson pattern, not a hack, but still important to note.

### Improvements

**UX**
- [ ] Dark/light mode toggle
- [ ] Show "No results found" state when MCP returns empty
- [ ] Loading skeleton instead of "Searching hotels..." text
- [ ] Show hotel count ("Found 8 hotels in Paris")

**Chat Quality**
- [ ] Pass stored preferences as MCP `filters` (e.g. `pool: true`, `breakfastIncluded: true`)
- [ ] Support `children` and `children_ages` params
- [ ] Support `hotel_rating` and `review_rating` filters from MCP

**Data**
- [ ] Cap search history (keep last 20, delete older)
- [ ] Add "clear history" button
- [ ] Add "clear preferences" button

**Polish**
- [ ] Show extracted search params before results load ("Searching Paris, 20-24 Dec, 2 adults...")
- [ ] Add a favicon
- [ ] Add Open Graph meta tags for link previews

**Infra**
- [ ] Add proper test coverage on the CI
