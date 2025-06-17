require('dotenv').config();
const {
  Client,
  GatewayIntentBits,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  StringSelectMenuBuilder,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
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
    .toJSON(),
  new SlashCommandBuilder()
    .setName('cm')
    .setDescription('ChatModeration commands')
    .addSubcommand(sc => sc.setName('menu').setDescription('Show command menu'))
    .addSubcommand(sc => sc.setName('mute').setDescription('Mute a player')
      .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true))
      .addIntegerOption(o => o.setName('minutes').setDescription('Duration in minutes').setRequired(true)))
    .addSubcommand(sc => sc.setName('unmute').setDescription('Unmute a player')
      .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true)))
    .addSubcommand(sc => sc.setName('status').setDescription('Check mute status')
      .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true)))
    .addSubcommand(sc => sc.setName('reload').setDescription('Reload plugin'))
    .addSubcommand(sc => sc.setName('logs').setDescription('Show logs')
      .addIntegerOption(o => o.setName('count').setDescription('Number of entries').setMinValue(1)))
    .addSubcommand(sc => sc.setName('clearlogs').setDescription('Clear log entries'))
    .toJSON()
];

const rest = new REST({ version: '10' }).setToken(token);

async function sendMenu(channel, ephemeralTarget) {
  const desc = [
    '/cm mute <player> <minutes> - mute player',
    '/cm unmute <player> - unmute player',
    '/cm status <player> - check mute status',
    '/cm reload - reload plugin',
    '/cm logs [count] - show logs',
    '/cm clearlogs - clear logs'
  ].join('\n');

  const menu = new StringSelectMenuBuilder()
    .setCustomId('cm-menu')
    .setPlaceholder('Choose command')
    .addOptions(
      { label: 'Mute', value: 'mute' },
      { label: 'Unmute', value: 'unmute' },
      { label: 'Status', value: 'status' },
      { label: 'Reload', value: 'reload' },
      { label: 'Logs', value: 'logs' },
      { label: 'Clear Logs', value: 'clearlogs' }
    );

  const row = new ActionRowBuilder().addComponents(menu);
  const payload = { content: desc, components: [row] };
  if (ephemeralTarget) {
    await ephemeralTarget.reply({ ...payload, ephemeral: true });
  } else {
    await channel.send(payload);
  }
}

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
  if (message.author.bot) return;
  if (message.content.startsWith(prefix + 'logs')) {
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
  } else if (message.content.trim() === prefix + 'cm') {
    await sendMenu(message.channel);
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
  } else if (interaction.isStringSelectMenu() && interaction.customId === 'cm-menu') {
    const choice = interaction.values[0];
    if (choice === 'reload') {
      await fetch(`${pluginUrl}/reload`, { method: 'POST' });
      await interaction.reply({ content: 'Plugin reloaded.', ephemeral: true });
    } else if (choice === 'clearlogs') {
      await fetch(`${pluginUrl}/clearlogs`, { method: 'POST' });
      await interaction.reply({ content: 'Logs cleared.', ephemeral: true });
    } else if (choice === 'logs') {
      const resp = await fetch(`${pluginUrl}/logs?count=5`);
      const logs = await resp.json();
      const latest = logs.map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await interaction.reply({ content: latest || 'No logs found.', ephemeral: true });
    } else if (choice === 'mute') {
      const modal = new ModalBuilder()
        .setCustomId('cm-mute')
        .setTitle('Mute Player')
        .addComponents(
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('player')
              .setLabel('Player')
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          ),
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('minutes')
              .setLabel('Minutes')
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          )
        );
      await interaction.showModal(modal);
    } else if (choice === 'unmute') {
      const modal = new ModalBuilder()
        .setCustomId('cm-unmute')
        .setTitle('Unmute Player')
        .addComponents(
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('player')
              .setLabel('Player')
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          )
        );
      await interaction.showModal(modal);
    } else if (choice === 'status') {
      const modal = new ModalBuilder()
        .setCustomId('cm-status')
        .setTitle('Status')
        .addComponents(
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('player')
              .setLabel('Player')
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          )
        );
      await interaction.showModal(modal);
    }
  } else if (interaction.isModalSubmit()) {
    if (interaction.customId === 'cm-mute') {
      const player = interaction.fields.getTextInputValue('player');
      const minutes = parseInt(interaction.fields.getTextInputValue('minutes'), 10);
      await fetch(`${pluginUrl}/mute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player, minutes })
      });
      await interaction.reply({ content: `Muted ${player} for ${minutes} minutes`, ephemeral: true });
    } else if (interaction.customId === 'cm-unmute') {
      const player = interaction.fields.getTextInputValue('player');
      await fetch(`${pluginUrl}/unmute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player })
      });
      await interaction.reply({ content: `Unmuted ${player}`, ephemeral: true });
    } else if (interaction.customId === 'cm-status') {
      const player = interaction.fields.getTextInputValue('player');
      const resp = await fetch(`${pluginUrl}/status?player=${encodeURIComponent(player)}`);
      const data = await resp.json();
      if (data.muted) {
        await interaction.reply({ content: `${player} muted for ${data.remaining}m`, ephemeral: true });
      } else {
        await interaction.reply({ content: `${player} is not muted`, ephemeral: true });
      }
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
  } else if (interaction.isChatInputCommand() && interaction.commandName === 'cm') {
    const sub = interaction.options.getSubcommand();
    if (sub === 'menu') {
      await sendMenu(null, interaction);
    } else if (sub === 'reload') {
      await fetch(`${pluginUrl}/reload`, { method: 'POST' });
      await interaction.reply({ content: 'Plugin reloaded.', ephemeral: true });
    } else if (sub === 'clearlogs') {
      await fetch(`${pluginUrl}/clearlogs`, { method: 'POST' });
      await interaction.reply({ content: 'Logs cleared.', ephemeral: true });
    } else if (sub === 'logs') {
      const count = interaction.options.getInteger('count') ?? 5;
      const resp = await fetch(`${pluginUrl}/logs?count=${count}`);
      const data = await resp.json();
      const latest = data.map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await interaction.reply({ content: latest || 'No logs found.', ephemeral: true });
    } else if (sub === 'mute') {
      const player = interaction.options.getString('player');
      const minutes = interaction.options.getInteger('minutes');
      await fetch(`${pluginUrl}/mute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player, minutes })
      });
      await interaction.reply({ content: `Muted ${player} for ${minutes} minutes`, ephemeral: true });
    } else if (sub === 'unmute') {
      const player = interaction.options.getString('player');
      await fetch(`${pluginUrl}/unmute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player })
      });
      await interaction.reply({ content: `Unmuted ${player}`, ephemeral: true });
    } else if (sub === 'status') {
      const player = interaction.options.getString('player');
      const resp = await fetch(`${pluginUrl}/status?player=${encodeURIComponent(player)}`);
      const data = await resp.json();
      if (data.muted) {
        await interaction.reply({ content: `${player} muted for ${data.remaining}m`, ephemeral: true });
      } else {
        await interaction.reply({ content: `${player} is not muted`, ephemeral: true });
      }
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
