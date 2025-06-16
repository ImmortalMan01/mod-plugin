# Discord Bot

This bot posts mute notifications from the ChatModeration plugin to a Discord channel.

## Setup

1. Copy `.env.example` to `.env` and fill in your bot token and the channel ID where notifications should be sent.

```
cp .env.example .env
# edit .env
```

2. Install dependencies (already done when `npm install` was run during setup):

```
npm install
```

3. Run the bot:

```
node bot.js
```

The bot reads `../data/logs.json` created by the plugin. When new entries are added, it sends a message to the configured Discord channel containing the player's name, muted message and timestamp.
