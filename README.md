## Travel AI Chat

Built primarily to help you find and manage hotel booking effortlessly. Powered by Trivago MCP Server (and some other tech stack).

### MCP Server Setup

Add the Trivago MCP server to Claude Code:

```bash
claude mcp add --transport sse trivago --scope project https://mcp.trivago.com/mcp
```

### Improvements

- [ ] Wrap chat API response in JSON (`ChatResponse` record) instead of returning plain text, for structured frontend rendering
- [ ] Add `GET /api/best-deals` endpoint that calls agent with "Show best deals from my search history"
- [ ] Fix Trivago `/oar/` URLs redirecting incorrectly (may be localhost/dev issue — retest after deployment)
