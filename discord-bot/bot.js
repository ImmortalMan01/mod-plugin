const { Client, GatewayIntentBits } = require('discord.js');
const fs = require('fs');
require('dotenv').config();

const token = process.env.DISCORD_TOKEN;
const channelId = process.env.CHANNEL_ID;
if (!token || !channelId) {
  console.error('DISCORD_TOKEN and CHANNEL_ID must be set in .env');
  process.exit(1);
}

const client = new Client({ intents: [GatewayIntentBits.Guilds] });

let knownEntries = new Set();
function loadLogs() {
  try {
    const raw = fs.readFileSync('../data/logs.json', 'utf8');
    const arr = JSON.parse(raw);
    arr.forEach(entry => knownEntries.add(entry.timestamp));
  } catch (err) {
    if (err.code !== 'ENOENT') console.error('Failed to load logs:', err);
  }
}

function checkForUpdates() {
  fs.readFile('../data/logs.json', 'utf8', (err, data) => {
    if (err) {
      if (err.code !== 'ENOENT') console.error('Read error:', err);
      return;
    }
    try {
      const arr = JSON.parse(data);
      arr.forEach(entry => {
        if (!knownEntries.has(entry.timestamp)) {
          knownEntries.add(entry.timestamp);
          sendNotification(entry);
        }
      });
    } catch (e) {
      console.error('Parse error:', e);
    }
  });
}

function sendNotification(entry) {
  const channel = client.channels.cache.get(channelId);
  if (!channel) return;
  const time = new Date(entry.timestamp).toLocaleString();
  const message = `**${entry.name}** was muted for:\n> ${entry.message}\n\n*${time}*`;
  channel.send(message).catch(console.error);
}

client.once('ready', () => {
  console.log(`Logged in as ${client.user.tag}`);
  loadLogs();
  checkForUpdates();
  fs.watchFile('../data/logs.json', { interval: 1000 }, checkForUpdates);
});

client.login(token);
