# PULSE FOUNDER Agent — FND-026

Private internal alert/confirmation service for the executive command layer.

Required environment:
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY` (server-side only)
- `PULSE_FOUNDER_ORGANIZATION_ID`
- `PULSE_FOUNDER_USER_ID`
- `PULSE_FOUNDER_AGENT_SECRET`

The service exposes `/health`, `/alert`, and `/confirmation`. `/alert` and `/confirmation` require the `X-Founder-Agent-Secret` header. The agent writes only to founder-scoped tables and never exposes the Supabase service-role key.

The agent does not claim invisibility, bypass lawful monitoring, or provide offensive surveillance capabilities.
