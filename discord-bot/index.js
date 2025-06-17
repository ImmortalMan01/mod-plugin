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
let lang = /^tr/.test(process.env.BOT_LANG || process.env.LANG) ? 'tr' : 'en';

const translations = {
  en: {
    logsDescription: 'Display recent log entries',
    countOption: 'Number of entries to show',
    cmDescription: 'ChatModeration commands',
    showCommandMenu: 'Show command menu',
    muteDescription: 'Mute a player',
    playerOption: 'Player name',
    minutesOption: 'Duration in minutes',
    unmuteDescription: 'Unmute a player',
    statusDescription: 'Check mute status',
    reloadDescription: 'Reload plugin',
    logsSubDescription: 'Show logs',
    entriesOption: 'Number of entries',
    clearLogsDescription: 'Clear log entries',
    menuLines: [
      '/cm mute <player> <minutes> - mute player',
      '/cm unmute <player> - unmute player',
      '/cm status <player> - check mute status',
      '/cm reload - reload plugin',
      '/cm logs [count] - show logs',
      '/cm clearlogs - clear logs'
    ],
    chooseCommand: 'Choose command',
    muteLabel: 'Mute',
    unmuteLabel: 'Unmute',
    statusLabel: 'Status',
    reloadLabel: 'Reload',
    logsLabel: 'Logs',
    clearLogsLabel: 'Clear Logs',
    muteTitle: 'Mute Player',
    unmuteTitle: 'Unmute Player',
    statusTitle: 'Status',
    playerLabel: 'Player',
    minutesLabel: 'Minutes',
    unmuteButton: 'Unmute',
    unmuteFail: 'Failed to unmute.',
    noLogs: 'No logs found.',
    readError: 'Could not read log file.',
    reloaded: 'Plugin reloaded.',
    cleared: 'Logs cleared.',
    muted: 'Muted {player} for {minutes} minutes',
    unmuted: 'Unmuted {player}',
    mutedFor: '{player} muted for {remaining}m',
    notMuted: '{player} is not muted',
    apiMute: 'Player **{player}** was muted for {minutes}m. Reason: {reason}',
    langDesc: 'Change bot language',
    langOption: 'Language code',
    langSet: 'Language set to {lang}',
    invalidLang: 'Invalid language code'
  },
  tr: {
    logsDescription: 'Son kayıtları göster',
    countOption: 'Gösterilecek kayıt sayısı',
    cmDescription: 'ChatModeration komutları',
    showCommandMenu: 'Komut menüsünü göster',
    muteDescription: 'Oyuncuyu sustur',
    playerOption: 'Oyuncu adı',
    minutesOption: 'Süre (dakika)',
    unmuteDescription: 'Susturmayı kaldır',
    statusDescription: 'Susturma durumunu kontrol et',
    reloadDescription: 'Eklentiyi yeniden yükle',
    logsSubDescription: 'Logları göster',
    entriesOption: 'Kayıt sayısı',
    clearLogsDescription: 'Logları temizle',
    menuLines: [
      '/cm mute <oyuncu> <dakika> - oyuncuyu sustur',
      '/cm unmute <oyuncu> - susturmayı kaldır',
      '/cm status <oyuncu> - susturma durumunu kontrol et',
      '/cm reload - eklentiyi yeniden yükle',
      '/cm logs [sayı] - logları göster',
      '/cm clearlogs - logları temizle'
    ],
    chooseCommand: 'Komut seç',
    muteLabel: 'Sustur',
    unmuteLabel: 'Susturmayı kaldır',
    statusLabel: 'Durum',
    reloadLabel: 'Yeniden Yükle',
    logsLabel: 'Loglar',
    clearLogsLabel: 'Logları Temizle',
    muteTitle: 'Oyuncuyu Sustur',
    unmuteTitle: 'Susturmayı Kaldır',
    statusTitle: 'Durum',
    playerLabel: 'Oyuncu',
    minutesLabel: 'Dakika',
    unmuteButton: 'Susturmayı Kaldır',
    unmuteFail: 'Susturma kaldırılamadı.',
    noLogs: 'Kayıt bulunamadı.',
    readError: 'Log dosyası okunamadı.',
    reloaded: 'Eklenti yeniden yüklendi.',
    cleared: 'Loglar temizlendi.',
    muted: '{player} {minutes} dakika susturuldu',
    unmuted: '{player} susturması kaldırıldı',
    mutedFor: '{player} {remaining}dk susturulmuş',
    notMuted: '{player} susturulmamış',
    apiMute: '**{player}** oyuncusu {minutes}dk susturuldu. Sebep: {reason}',
    langDesc: 'Bot dilini değiştir',
    langOption: 'Dil kodu',
    langSet: 'Dil {lang} olarak ayarlandı',
    invalidLang: 'Geçersiz dil kodu'
  }
};

function t(key, vars = {}) {
  let str = (translations[lang] && translations[lang][key]) || translations.en[key] || key;
  for (const [k, v] of Object.entries(vars)) str = str.replace(`{${k}}`, v);
  return str;
}

const commands = [
  new SlashCommandBuilder()
    .setName('logs')
    .setNameLocalizations({ tr: 'loglar' })
    .setDescription(t('logsDescription'))
    .setDescriptionLocalizations({ tr: translations.tr.logsDescription })
    .addIntegerOption(o =>
      o.setName('count')
        .setNameLocalizations({ tr: 'sayi' })
        .setDescription(t('countOption'))
        .setDescriptionLocalizations({ tr: translations.tr.countOption })
        .setMinValue(1))
    .toJSON(),
  new SlashCommandBuilder()
    .setName('cm')
    .setDescription(t('cmDescription'))
    .setDescriptionLocalizations({ tr: translations.tr.cmDescription })
    .addSubcommand(sc => sc.setName('menu')
      .setDescription(t('showCommandMenu'))
      .setDescriptionLocalizations({ tr: translations.tr.showCommandMenu }))
    .addSubcommand(sc => sc.setName('mute')
      .setDescription(t('muteDescription'))
      .setDescriptionLocalizations({ tr: translations.tr.muteDescription })
      .addStringOption(o => o.setName('player')
        .setNameLocalizations({ tr: 'oyuncu' })
        .setDescription(t('playerOption'))
        .setDescriptionLocalizations({ tr: translations.tr.playerOption })
        .setRequired(true))
      .addIntegerOption(o => o.setName('minutes')
        .setNameLocalizations({ tr: 'dakika' })
        .setDescription(t('minutesOption'))
        .setDescriptionLocalizations({ tr: translations.tr.minutesOption })
        .setRequired(true)))
    .addSubcommand(sc => sc.setName('unmute')
      .setDescription(t('unmuteDescription'))
      .setDescriptionLocalizations({ tr: translations.tr.unmuteDescription })
      .addStringOption(o => o.setName('player')
        .setNameLocalizations({ tr: 'oyuncu' })
        .setDescription(t('playerOption'))
        .setDescriptionLocalizations({ tr: translations.tr.playerOption })
        .setRequired(true)))
    .addSubcommand(sc => sc.setName('status')
      .setDescription(t('statusDescription'))
      .setDescriptionLocalizations({ tr: translations.tr.statusDescription })
      .addStringOption(o => o.setName('player')
        .setNameLocalizations({ tr: 'oyuncu' })
        .setDescription(t('playerOption'))
        .setDescriptionLocalizations({ tr: translations.tr.playerOption })
        .setRequired(true)))
    .addSubcommand(sc => sc.setName('reload')
      .setDescription(t('reloadDescription'))
      .setDescriptionLocalizations({ tr: translations.tr.reloadDescription }))
    .addSubcommand(sc => sc.setName('logs')
      .setDescription(t('logsSubDescription'))
      .setDescriptionLocalizations({ tr: translations.tr.logsSubDescription })
      .addIntegerOption(o => o.setName('count')
        .setNameLocalizations({ tr: 'sayi' })
        .setDescription(t('entriesOption'))
        .setDescriptionLocalizations({ tr: translations.tr.entriesOption })
        .setMinValue(1)))
    .addSubcommand(sc => sc.setName('clearlogs')
      .setDescription(t('clearLogsDescription'))
      .setDescriptionLocalizations({ tr: translations.tr.clearLogsDescription }))
    .toJSON(),
  new SlashCommandBuilder()
    .setName('lang')
    .setNameLocalizations({ tr: 'dil' })
    .setDescription(t('langDesc'))
    .setDescriptionLocalizations({ tr: translations.tr.langDesc })
    .addStringOption(o => o.setName('lang')
      .setDescription(t('langOption'))
      .setDescriptionLocalizations({ tr: translations.tr.langOption })
      .setChoices(
        { name: 'en', value: 'en' },
        { name: 'tr', value: 'tr' }
      )
      .setRequired(true))
    .toJSON()
];

const rest = new REST({ version: '10' }).setToken(token);

async function sendMenu(channel, ephemeralTarget) {
  const desc = translations[lang].menuLines.join('\n');

  const menu = new StringSelectMenuBuilder()
    .setCustomId('cm-menu')
    .setPlaceholder(t('chooseCommand'))
    .addOptions(
      { label: t('muteLabel'), value: 'mute' },
      { label: t('unmuteLabel'), value: 'unmute' },
      { label: t('statusLabel'), value: 'status' },
      { label: t('reloadLabel'), value: 'reload' },
      { label: t('logsLabel'), value: 'logs' },
      { label: t('clearLogsLabel'), value: 'clearlogs' }
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
      await message.channel.send({ content: latest || t('noLogs') });
    } catch (err) {
      console.error('Failed to read log file', err);
      await message.channel.send({ content: t('readError') });
    }
  } else if (message.content.trim().startsWith(prefix + 'lang')) {
    const parts = message.content.trim().split(/\s+/);
    const newLang = parts[1];
    if (['en', 'tr'].includes(newLang)) {
      lang = newLang;
      await message.channel.send({ content: t('langSet', { lang: newLang }) });
    } else {
      await message.channel.send({ content: t('invalidLang') });
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
      await interaction.reply({ content: t('unmuted', { player }), ephemeral: true });
    } catch (err) {
      console.error('Failed to unmute', err);
      await interaction.reply({ content: t('unmuteFail'), ephemeral: true });
    }
  } else if (interaction.isStringSelectMenu() && interaction.customId === 'cm-menu') {
    const choice = interaction.values[0];
    if (choice === 'reload') {
      await fetch(`${pluginUrl}/reload`, { method: 'POST' });
      await interaction.reply({ content: t('reloaded'), ephemeral: true });
    } else if (choice === 'clearlogs') {
      await fetch(`${pluginUrl}/clearlogs`, { method: 'POST' });
      await interaction.reply({ content: t('cleared'), ephemeral: true });
    } else if (choice === 'logs') {
      const resp = await fetch(`${pluginUrl}/logs?count=5`);
      const logs = await resp.json();
      const latest = logs.map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await interaction.reply({ content: latest || t('noLogs'), ephemeral: true });
    } else if (choice === 'mute') {
      const modal = new ModalBuilder()
        .setCustomId('cm-mute')
        .setTitle(t('muteTitle'))
        .addComponents(
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('player')
              .setLabel(t('playerLabel'))
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          ),
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('minutes')
              .setLabel(t('minutesLabel'))
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          )
        );
      await interaction.showModal(modal);
    } else if (choice === 'unmute') {
      const modal = new ModalBuilder()
        .setCustomId('cm-unmute')
        .setTitle(t('unmuteTitle'))
        .addComponents(
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('player')
              .setLabel(t('playerLabel'))
              .setStyle(TextInputStyle.Short)
              .setRequired(true)
          )
        );
      await interaction.showModal(modal);
    } else if (choice === 'status') {
      const modal = new ModalBuilder()
        .setCustomId('cm-status')
        .setTitle(t('statusTitle'))
        .addComponents(
          new ActionRowBuilder().addComponents(
            new TextInputBuilder()
              .setCustomId('player')
              .setLabel(t('playerLabel'))
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
      await interaction.reply({ content: t('muted', { player, minutes }), ephemeral: true });
    } else if (interaction.customId === 'cm-unmute') {
      const player = interaction.fields.getTextInputValue('player');
      await fetch(`${pluginUrl}/unmute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player })
      });
      await interaction.reply({ content: t('unmuted', { player }), ephemeral: true });
    } else if (interaction.customId === 'cm-status') {
      const player = interaction.fields.getTextInputValue('player');
      const resp = await fetch(`${pluginUrl}/status?player=${encodeURIComponent(player)}`);
      const data = await resp.json();
      if (data.muted) {
        await interaction.reply({ content: t('mutedFor', { player, remaining: data.remaining }), ephemeral: true });
      } else {
        await interaction.reply({ content: t('notMuted', { player }), ephemeral: true });
      }
    }
  } else if (interaction.isChatInputCommand() && interaction.commandName === 'logs') {
    const count = interaction.options.getInteger('count') ?? 5;
    try {
      const logs = await fs.readJson(logFile);
      const latest = logs.slice(-count).map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await interaction.reply({ content: latest || t('noLogs') });
    } catch (err) {
      console.error('Failed to read log file', err);
      await interaction.reply({ content: t('readError') });
    }
  } else if (interaction.isChatInputCommand() && interaction.commandName === 'lang') {
    const newLang = interaction.options.getString('lang');
    lang = newLang;
    await interaction.reply({ content: t('langSet', { lang: newLang }), ephemeral: true });
  } else if (interaction.isChatInputCommand() && interaction.commandName === 'cm') {
    const sub = interaction.options.getSubcommand();
    if (sub === 'menu') {
      await sendMenu(null, interaction);
    } else if (sub === 'reload') {
      await fetch(`${pluginUrl}/reload`, { method: 'POST' });
      await interaction.reply({ content: t('reloaded'), ephemeral: true });
    } else if (sub === 'clearlogs') {
      await fetch(`${pluginUrl}/clearlogs`, { method: 'POST' });
      await interaction.reply({ content: t('cleared'), ephemeral: true });
    } else if (sub === 'logs') {
      const count = interaction.options.getInteger('count') ?? 5;
      const resp = await fetch(`${pluginUrl}/logs?count=${count}`);
      const data = await resp.json();
      const latest = data.map(l => `${new Date(l.timestamp).toLocaleString()} - **${l.name}**: ${l.message}`).join('\n');
      await interaction.reply({ content: latest || t('noLogs'), ephemeral: true });
    } else if (sub === 'mute') {
      const player = interaction.options.getString('player');
      const minutes = interaction.options.getInteger('minutes');
      await fetch(`${pluginUrl}/mute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player, minutes })
      });
      await interaction.reply({ content: t('muted', { player, minutes }), ephemeral: true });
    } else if (sub === 'unmute') {
      const player = interaction.options.getString('player');
      await fetch(`${pluginUrl}/unmute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player })
      });
      await interaction.reply({ content: t('unmuted', { player }), ephemeral: true });
    } else if (sub === 'status') {
      const player = interaction.options.getString('player');
      const resp = await fetch(`${pluginUrl}/status?player=${encodeURIComponent(player)}`);
      const data = await resp.json();
      if (data.muted) {
        await interaction.reply({ content: t('mutedFor', { player, remaining: data.remaining }), ephemeral: true });
      } else {
        await interaction.reply({ content: t('notMuted', { player }), ephemeral: true });
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
          .setLabel(t('unmuteButton'))
          .setStyle(ButtonStyle.Danger)
      );
      await channel.send({
        content: t('apiMute', { player, minutes: remaining, reason }),
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
