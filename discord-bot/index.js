require('dotenv').config();
const {
  Client,
  GatewayIntentBits,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  SlashCommandBuilder,
  REST,
  Routes
} = require('discord.js');
const express = require('express');
const fs = require('fs-extra');

const token = process.env.DISCORD_TOKEN;
const channelId = process.env.CHANNEL_ID || '1383833781483471020';
const logFile = process.env.LOG_FILE || '../plugins/ChatModeration/data/logs.json';
const port = process.env.PORT || 3000;
const prefixEnv = process.env.PREFIX;
const prefix = ['!', '/'].includes(prefixEnv) ? prefixEnv : '!';
const pluginUrl = process.env.PLUGIN_URL || 'http://localhost:8081';
const clientId = process.env.CLIENT_ID;
const guildId = process.env.GUILD_ID;

const commands = [
  new SlashCommandBuilder()
    .setName('logs')
    .setDescription('Display recent log entries')
    .addIntegerOption(o =>
      o.setName('count')
        .setDescription('Number of entries to show')
        .setMinValue(1))
    .toJSON()
];

const rest = new REST({ version: '10' }).setToken(token);

(async () => {
  try {
    console.log('Registering slash commands...');
    if (guildId) {
      await rest.put(Routes.applicationGuildCommands(clientId, guildId), { body: commands });
    } else {
      await rest.put(Routes.applicationCommands(clientId), { body: commands });
    }
    console.log('Slash commands registered.');
  } catch (err) {
    console.error('Failed to register commands', err);
  }
})();

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

client.on('interactionCreate', async (interaction) => {
  if (interaction.isButton()) {
    if (!interaction.customId.startsWith('unmute:')) return;
    const player = interaction.customId.substring(7);
    try {
      await fetch(`${pluginUrl}/unmute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player })
      });
      await interaction.reply({ content: `Unmuted ${player}`, ephemeral: true });
    } catch (err) {
      console.error('Failed to unmute', err);
      await interaction.reply({ content: 'Failed to unmute.', ephemeral: true });
    }
  } else if (interaction.isChatInputCommand() && interaction.commandName === 'logs') {
    const count = interaction.options.getInteger('count') ?? 5;
    try {
      const logs = await fs.readJson(logFile);
      const latest = logs.slice(-count).map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await interaction.reply({ content: latest || 'No logs found.' });
    } catch (err) {
      console.error('Failed to read log file', err);
      await interaction.reply({ content: 'Could not read log file.' });
    }
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
      const row = new ActionRowBuilder().addComponents(
        new ButtonBuilder()
          .setCustomId(`unmute:${player}`)
          .setLabel('Unmute')
          .setStyle(ButtonStyle.Danger)
      );
      await channel.send({
        content: `Player **${player}** was muted for ${remaining}m. Reason: ${reason}`,
        components: [row]
      });
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
