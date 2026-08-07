# Team chat notifications

The orchestrator can post a message to your team's chat when an **interview result is
finalized** (the same action that emails the candidate their result). This is team-facing
and complements the candidate email — reviewers/recruiters get a heads-up with the outcome.

Supported providers, each via a standard **incoming webhook** (no OAuth / bot tokens):

| Provider | Env var | Payload |
|---|---|---|
| Slack | `SLACK_WEBHOOK_URL` | text + coloured attachment with fields |
| Microsoft Teams | `TEAMS_WEBHOOK_URL` | MessageCard (connector / Workflows) |
| Discord | `DISCORD_WEBHOOK_URL` | rich embed |
| Google Chat | `GOOGLE_CHAT_WEBHOOK_URL` | markdown text |
| Generic webhook | `CHAT_GENERIC_WEBHOOK_URL` | neutral structured JSON |

## Enable it

Set `CHAT_NOTIFICATIONS_ENABLED=true` and supply one or more webhook URLs. Any provider
without a URL is skipped, so you can enable just the ones you use. All are **off by default**.

```bash
CHAT_NOTIFICATIONS_ENABLED=true
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/T000/B000/xxxx
# TEAMS_WEBHOOK_URL=...
# DISCORD_WEBHOOK_URL=...
```

Where to get a webhook URL:
- **Slack** — create an app → Incoming Webhooks → Add New Webhook to a channel.
- **Microsoft Teams** — channel → Connectors → Incoming Webhook (or a Workflows webhook).
- **Discord** — channel → Edit → Integrations → Webhooks → New Webhook → Copy URL.
- **Google Chat** — space → Apps & integrations → Manage webhooks → Add.

## Behaviour

Delivery is **best-effort**: a provider being unconfigured is skipped, and a delivery
failure is logged (`event=chat.notify_failed`) but never propagates — a chat outage can never
fail the interview-result flow that triggered it. See
`com.onlineinterview.notification.chat` (`TeamChatNotifier`, `ChatMessageFormatter`).

## Extending

To add another provider: add a value to `ChatProvider`, a URL field to
`ChatNotificationProperties`, a `format(...)` branch in `ChatMessageFormatter`, and a `post(...)`
call in `TeamChatNotifier.notify(...)`. To notify on more events (e.g. candidate submitted),
build a `ChatMessage` at that call site and invoke `TeamChatNotifier.notify(...)`.
