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

let lang = (process.env.BOT_LANG || 'en').toLowerCase() === 'tr' ? 'tr' : 'en';

const strings = {
  en: {
    menuLines: [
      '/cm mute <player> <minutes> - mute player',
      '/cm unmute <player> - unmute player',
      '/cm status <player> - check mute status',
      '/cm reload - reload plugin',
      '/cm logs [count] - show logs',
      '/cm clearlogs - clear logs',
      '/cm lang <en|tr> - change language'
    ],
    choose: 'Choose command',
    opts: {
      mute: 'Mute',
      unmute: 'Unmute',
      status: 'Status',
      reload: 'Reload',
      logs: 'Logs',
      clearlogs: 'Clear Logs',
      lang: 'Language'
    },
    modalMute: 'Mute Player',
    modalUnmute: 'Unmute Player',
    modalStatus: 'Status',
    labelPlayer: 'Player',
    labelMinutes: 'Minutes',
    noLogs: 'No logs found.',
    logError: 'Could not read log file.',
    pluginReloaded: 'Plugin reloaded.',
    logsCleared: 'Logs cleared.',
    muted: 'Muted {0} for {1} minutes',
    unmuted: 'Unmuted {0}',
    failedUnmute: 'Failed to unmute.',
    statusMuted: '{0} muted for {1}m',
    statusUnmuted: '{0} is not muted',
    langChanged: 'Language set to {0}.',
    langUsage: 'Use /cm lang <en|tr> to change language.',
    apiMute: 'Player **{0}** was muted for {1}m. Reason: {2}',
    buttonUnmute: 'Unmute'
  },
  tr: {
    menuLines: [
      '/cm mute <oyuncu> <dakika> - oyuncuyu sustur',
      '/cm unmute <oyuncu> - susturmayı kaldır',
      '/cm status <oyuncu> - susturma durumunu kontrol et',
      '/cm reload - eklentiyi yenile',
      '/cm logs [adet] - logları göster',
      '/cm clearlogs - logları temizle',
      '/cm lang <en|tr> - dili değiştir'
    ],
    choose: 'Komut seçin',
    opts: {
      mute: 'Sustur',
      unmute: 'Kaldır',
      status: 'Durum',
      reload: 'Yenile',
      logs: 'Loglar',
      clearlogs: 'Log temizle',
      lang: 'Dil'
    },
    modalMute: 'Oyuncuyu Sustur',
    modalUnmute: 'Susturmayı Kaldır',
    modalStatus: 'Durum',
    labelPlayer: 'Oyuncu',
    labelMinutes: 'Dakika',
    noLogs: 'Log bulunamadı.',
    logError: 'Log dosyası okunamadı.',
    pluginReloaded: 'Eklenti yenilendi.',
    logsCleared: 'Loglar temizlendi.',
    muted: '{0} {1} dakika susturuldu',
    unmuted: '{0} susturması kaldırıldı',
    failedUnmute: 'Susturma kaldırılamadı.',
    statusMuted: '{0} {1}dk susturulmuş',
    statusUnmuted: '{0} susturulmamış',
    langChanged: 'Dil {0} olarak ayarlandı.',
    langUsage: '/cm lang <en|tr> komutunu kullanın.',
    apiMute: 'Oyuncu **{0}** {1}dk susturuldu. Sebep: {2}',
    buttonUnmute: 'Kaldır'
  }
};

function t(key, ...args) {
  const str = (strings[lang][key] || strings.en[key] || key).toString();
  return str.replace(/\{(\d+)\}/g, (_, n) => args[n] ?? '');
}

function buildCommands() {
  return [
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
      .addSubcommand(sc => sc.setName('lang').setDescription('Change language')
        .addStringOption(o => o.setName('lang').setDescription('Language').setRequired(true)
          .addChoices({ name: 'English', value: 'en' }, { name: 'Türkçe', value: 'tr' })))
      .toJSON()
  ];
}

const rest = new REST({ version: '10' }).setToken(token);

async function sendMenu(channel, ephemeralTarget) {
  const desc = strings[lang].menuLines.join('\n');

  const menu = new StringSelectMenuBuilder()
    .setCustomId('cm-menu')
    .setPlaceholder(strings[lang].choose)
    .addOptions(
      { label: strings[lang].opts.mute, value: 'mute' },
      { label: strings[lang].opts.unmute, value: 'unmute' },
      { label: strings[lang].opts.status, value: 'status' },
      { label: strings[lang].opts.reload, value: 'reload' },
      { label: strings[lang].opts.logs, value: 'logs' },
      { label: strings[lang].opts.clearlogs, value: 'clearlogs' },
      { label: strings[lang].opts.lang, value: 'lang' }
    );

  const row = new ActionRowBuilder().addComponents(menu);
  const payload = { content: desc, components: [row] };
  if (ephemeralTarget) {
    await ephemeralTarget.reply({ ...payload, ephemeral: true });
  } else {
    await channel.send(payload);
  }
}

async function registerCommands() {
  try {
    console.log('Registering slash commands...');
    const commands = buildCommands();
    if (guildId) {
      await rest.put(Routes.applicationGuildCommands(clientId, guildId), { body: commands });
    } else {
      await rest.put(Routes.applicationCommands(clientId), { body: commands });
    }
    console.log('Slash commands registered.');
  } catch (err) {
    console.error('Failed to register commands', err);
  }
}

(async () => {
  await registerCommands();
})();

const client = new Client({
  intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages, GatewayIntentBits.MessageContent]
});

client.once('ready', () => {
  console.log('Discord bot ready');
});

client.on('messageCreate', async (message) => {
  if (message.author.bot) return;
  const trimmed = message.content.trim();
  if (trimmed.startsWith(prefix + 'logs')) {
    const parts = trimmed.split(/\s+/);
    const count = parseInt(parts[1], 10) || 5;
    try {
      const logs = await fs.readJson(logFile);
      const latest = logs.slice(-count).map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await message.channel.send({ content: latest || strings[lang].noLogs });
    } catch (err) {
      console.error('Failed to read log file', err);
      await message.channel.send({ content: strings[lang].logError });
    }
  } else if (trimmed.startsWith(prefix + 'cm lang')) {
    const parts = trimmed.split(/\s+/);
    const newLang = parts[2];
    if (newLang === 'en' || newLang === 'tr') {
      lang = newLang;
      await registerCommands();
      await message.channel.send({ content: t('langChanged', newLang) });
    }
  } else if (trimmed === prefix + 'cm') {
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
      await interaction.reply({ content: t('unmuted', player), ephemeral: true });
    } catch (err) {
      console.error('Failed to unmute', err);
      await interaction.reply({ content: strings[lang].failedUnmute, ephemeral: true });
    }
  } else if (interaction.isStringSelectMenu() && interaction.customId === 'cm-menu') {
    const choice = interaction.values[0];
    if (choice === 'reload') {
      await fetch(`${pluginUrl}/reload`, { method: 'POST' });
      await interaction.reply({ content: strings[lang].pluginReloaded, ephemeral: true });
    } else if (choice === 'clearlogs') {
      await fetch(`${pluginUrl}/clearlogs`, { method: 'POST' });
      await interaction.reply({ content: strings[lang].logsCleared, ephemeral: true });
    } else if (choice === 'logs') {
      const resp = await fetch(`${pluginUrl}/logs?count=5`);
      const logs = await resp.json();
      const latest = logs.map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await interaction.reply({ content: latest || strings[lang].noLogs, ephemeral: true });
    } else if (choice === 'mute') {
      const modal = new ModalBuilder()
        .setCustomId('cm-mute')
        .setTitle(strings[lang].modalMute)
        .addComponents(
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('player')
              .setLabel(strings[lang].labelPlayer)
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          ),
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('minutes')
              .setLabel(strings[lang].labelMinutes)
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          )
        );
      await interaction.showModal(modal);
    } else if (choice === 'unmute') {
      const modal = new ModalBuilder()
        .setCustomId('cm-unmute')
        .setTitle(strings[lang].modalUnmute)
        .addComponents(
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('player')
              .setLabel(strings[lang].labelPlayer)
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          )
        );
      await interaction.showModal(modal);
    } else if (choice === 'status') {
      const modal = new ModalBuilder()
        .setCustomId('cm-status')
        .setTitle(strings[lang].modalStatus)
        .addComponents(
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('player')
              .setLabel(strings[lang].labelPlayer)
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          )
        );
      await interaction.showModal(modal);
    } else if (choice === 'lang') {
      await interaction.reply({ content: strings[lang].langUsage, ephemeral: true });
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
      await interaction.reply({ content: t('muted', player, minutes), ephemeral: true });
    } else if (interaction.customId === 'cm-unmute') {
      const player = interaction.fields.getTextInputValue('player');
      await fetch(`${pluginUrl}/unmute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player })
      });
      await interaction.reply({ content: t('unmuted', player), ephemeral: true });
    } else if (interaction.customId === 'cm-status') {
      const player = interaction.fields.getTextInputValue('player');
      const resp = await fetch(`${pluginUrl}/status?player=${encodeURIComponent(player)}`);
      const data = await resp.json();
      if (data.muted) {
        await interaction.reply({ content: t('statusMuted', player, data.remaining), ephemeral: true });
      } else {
        await interaction.reply({ content: t('statusUnmuted', player), ephemeral: true });
      }
    }
  } else if (interaction.isChatInputCommand() && interaction.commandName === 'logs') {
    const count = interaction.options.getInteger('count') ?? 5;
    try {
      const logs = await fs.readJson(logFile);
      const latest = logs.slice(-count).map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await interaction.reply({ content: latest || strings[lang].noLogs });
    } catch (err) {
      console.error('Failed to read log file', err);
      await interaction.reply({ content: strings[lang].logError });
    }
  } else if (interaction.isChatInputCommand() && interaction.commandName === 'cm') {
    const sub = interaction.options.getSubcommand();
    if (sub === 'menu') {
      await sendMenu(null, interaction);
    } else if (sub === 'reload') {
      await fetch(`${pluginUrl}/reload`, { method: 'POST' });
      await interaction.reply({ content: strings[lang].pluginReloaded, ephemeral: true });
    } else if (sub === 'clearlogs') {
      await fetch(`${pluginUrl}/clearlogs`, { method: 'POST' });
      await interaction.reply({ content: strings[lang].logsCleared, ephemeral: true });
    } else if (sub === 'logs') {
      const count = interaction.options.getInteger('count') ?? 5;
      const resp = await fetch(`${pluginUrl}/logs?count=${count}`);
      const data = await resp.json();
      const latest = data.map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await interaction.reply({ content: latest || strings[lang].noLogs, ephemeral: true });
    } else if (sub === 'mute') {
      const player = interaction.options.getString('player');
      const minutes = interaction.options.getInteger('minutes');
      await fetch(`${pluginUrl}/mute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player, minutes })
      });
      await interaction.reply({ content: t('muted', player, minutes), ephemeral: true });
    } else if (sub === 'unmute') {
      const player = interaction.options.getString('player');
      await fetch(`${pluginUrl}/unmute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player })
      });
      await interaction.reply({ content: t('unmuted', player), ephemeral: true });
    } else if (sub === 'status') {
      const player = interaction.options.getString('player');
      const resp = await fetch(`${pluginUrl}/status?player=${encodeURIComponent(player)}`);
      const data = await resp.json();
      if (data.muted) {
        await interaction.reply({ content: t('statusMuted', player, data.remaining), ephemeral: true });
      } else {
        await interaction.reply({ content: t('statusUnmuted', player), ephemeral: true });
      }
    } else if (sub === 'lang') {
      const newLang = interaction.options.getString('lang');
      if (newLang === 'en' || newLang === 'tr') {
        lang = newLang;
        await registerCommands();
        await interaction.reply({ content: t('langChanged', newLang), ephemeral: true });
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
          .setLabel(strings[lang].buttonUnmute)
          .setStyle(ButtonStyle.Danger)
      );
      await channel.send({
        content: t('apiMute', player, remaining, reason),
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
