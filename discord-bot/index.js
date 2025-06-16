require('dotenv').config();
const { Client, GatewayIntentBits } = require('discord.js');
const express = require('express');
const fs = require('fs-extra');

const token = process.env.DISCORD_TOKEN;
const channelId = process.env.CHANNEL_ID || '1383833781483471020';
const logFile = process.env.LOG_FILE || '../plugins/ChatModeration/data/logs.json';
const port = process.env.PORT || 3000;
const prefixEnv = process.env.PREFIX;
const prefix = ['!', '/'].includes(prefixEnv) ? prefixEnv : '!';

const client = new Client({
  intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages, GatewayIntentBits.MessageContent]
});

client.once('ready', () => {
  console.log('Discord bot ready');
});

client.on('messageCreate', async (message) => {
  if (!message.content.startsWith(prefix + 'logs')) return;
  const parts = message.content.trim().split(/\s+/);
  const count = parseInt(parts[1], 10) || 5;
  try {
    const logs = await fs.readJson(logFile);
    const latest = logs.slice(-count).map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
    await message.channel.send({ content: latest || 'No logs found.' });
  } catch (err) {
    console.error('Failed to read log file', err);
    await message.channel.send({ content: 'Could not read log file.' });
  }
});

const app = express();
app.use(express.json());

app.post('/mute', async (req, res) => {
  const { player, reason, remaining } = req.body;
  if (!player) return res.status(400).json({ error: 'Missing player' });
  try {
    const channel = await client.channels.fetch(channelId);
    if (channel && channel.isTextBased()) {
      await channel.send({ content: `Player **${player}** was muted for ${remaining}m. Reason: ${reason}` });
    }
    res.json({ ok: true });
  } catch (err) {
    console.error('Failed to send mute notification', err);
    res.status(500).json({ error: err.message });
  }
});

client.login(token).then(() => {
  app.listen(port, () => console.log(`API listening on ${port}`));
}).catch(err => {
  console.error('Discord login failed:', err);
});
