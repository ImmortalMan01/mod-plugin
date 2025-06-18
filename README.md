# ChatModeration

A simple Paper/Spigot plugin that integrates with OpenAI Moderation API to automatically mute players for inappropriate chat messages. The plugin demonstrates asynchronous HTTP communication, rate limiting and basic punishment storage.

## Building
Requires JDK 21. If the Gradle wrapper JAR is missing, run `gradle wrapper` or use your system Gradle.
The project depends on the [Snowball stemmer](https://github.com/snowballstem/snowball). Gradle will download this library automatically.

```
./gradlew shadowJar
# or
gradle shadowJar
```
The shaded jar will be created in `build/libs/ChatModeration.jar`.

## Testing

Run unit tests with:
```
./gradlew test
# or
gradle test
```

## Configuration
The plugin generates `config.yml` on first run. **You must replace** the `openai-key`
value with your own API key or moderation requests will be skipped. Adjust
thresholds or punishments as needed.
Set `language` to `en` or `tr` to change plugin messages. The selected language file (`messages_en.yml` or `messages_tr.yml`) will be copied to the plugin folder so you can edit any text.
Enable `debug: true` in `config.yml` to log moderation responses and the selected moderation model for troubleshooting.
Set `countdown-offline` to `false` if you want mute timers to pause while muted players are offline.
Muted players are also blocked from using private messaging commands like `/msg`.
The `unmute-threads` option controls how many threads the built-in web server uses to
process `/unmute` requests (default `10`).
The `moderation-cache-minutes` option controls how long moderation results are cached to avoid duplicate API calls (default `5`).
`http-connect-timeout` and `http-read-timeout` configure OkHttp timeouts in seconds (default `10`).
`http-max-requests` and `http-max-requests-per-host` adjust the HTTP dispatcher limits (default `100`).
The `model` option defaults to OpenAI's `omni-moderation-latest`, but you may set it to any supported model. When `gpt-4.1-mini`, `gpt-4.1`, `o3` or `o4-mini` is selected the plugin will use the chat completion API with a system prompt to simply answer whether the message contains profanity.
You can customize this system prompt via the `chat-prompt` option if you need different wording.
For reasoning models (`o3`, `o4-mini`), the `thinking-effort` option controls
the reasoning effort used (`low`, `medium`, or `high`). These models have no
token limit, while standard chat models are capped at **five** tokens to avoid
OpenAI errors.
All categories supported by this model are included in `blocked-categories`:

```
harassment
harassment/threatening
hate
hate/threatening
illicit
illicit/violent
self-harm
self-harm/intent
self-harm/instructions
sexual
sexual/minors
violence
violence/graphic
```
Set `use-blocked-categories` to `false` if you want to ignore this list and only
apply mutes when the API marks a message as `blocked`.
Each category can also be tuned individually under the `category-settings`
section. Set `enabled` to `false` to disable muting for a category or adjust the
`ratio` value to change the score threshold used for that category. If a
specific ratio is not provided, the global `threshold` option is used.
You can also define `blocked-words` for custom profanity detection. Any chat message
containing one of these words will be muted without an API call. **`use-blocked-words`
must be `true` for this filter to operate;** set it to `false` if you want to rely solely
on the OpenAI model.
The filter normalizes text when matching, converting Turkish letters like
`ş`, `ö`, `ç`, `ğ`, `ı` and `ü` to their ASCII equivalents and removing other
diacritics. Zero-width characters are stripped so they cannot be used to split
words. Confusable characters from other alphabets such as Cyrillic `а`, `ѕ`,
`е` or `і` are also mapped to their ASCII forms. Punctuation is converted to
spaces so word boundaries are kept.
Each token is checked against the block list and consecutive single-letter
tokens are combined, allowing `s i k` to match a blocked word of `sik`.
Characters can be remapped through the `character-mapping` section of
`config.yml`. By default digits are mapped to similar letters (for example
`s1k` becomes `sik`, `s2k` becomes `szk`, `g6k` becomes `ggk`, and `s9k`
may match `sgk` or `sqk`) and longer letter runs are collapsed so `siiiik`
also triggers.
Words within a small Levenshtein distance can also trigger the filter. The
`blocked-word-distance` option (default `1`) controls how many edits are
allowed when comparing each token to a blocked word.
Enable `use-stemming` if you want the filter to also match simple word stems.
For example a blocked word of `run` will also match `running` or `runner` when
stemming is active. The stemmer supports English and Turkish based on the
`language` setting.
You can also prefix and suffix an entry with `/` to use a regular expression.
These regex patterns are matched against the normalized text. For example

Example character mapping:
```yml
character-mapping:
  '0': 'o'
  '1': 'i'
  '2': 'z'
  '3': 'e'
  '4': 'a'
  '5': 's'
  '6': 'g'
  '7': 't'
  '8': 'b'
  '9': 'g'
```

`/bad(word)?/` would block both `bad` and `badword`.

### GUI Customization
A language-specific `gui_<lang>.yml` file controls the layout of the `/cm gui` dashboard. The `<lang>` part
matches the `language` setting (`en` or `tr`) so editing `gui_en.yml` will affect the English interface
while `gui_tr.yml` customizes the Turkish one. You can tweak inventory sizes, button slots and the
material/name for each menu item. Example:

```yml
main:
  size: 54
  title: "ChatModeration"
  player-slots: [0,9,18,27,36,45]
  buttons:
    reload:
      slot: 8
      material: BOOK
      name: "&eReload Config"
      action: reload
    clear:
      slot: 17
      material: PAPER
      name: "&eClear Offences"
      action: clear
    auto-mute:
      slot: 26
      material: LEVER
      name-on: "&eAuto-Mute ON"
      name-off: "&eAuto-Mute OFF"
      action: toggle-automute
    logs:
      slot: 35
      material: WRITABLE_BOOK
      name: "&eView Logs"
      action: logs
    clear-logs:
      slot: 44
      material: LAVA_BUCKET
      name: "&cClear Logs"
      action: clear-logs
player:
  size: 9
  buttons:
    add:
      slot: 0
      material: ARROW
      name: "&a+5m"
      action: add
      value: 5
    subtract:
      slot: 1
      material: ARROW
      name: "&c-5m"
      action: subtract
      value: 5
    unmute:
      slot: 8
      material: BARRIER
      name: "&cUnmute"
      action: unmute
```

Hovering a player's head shows whether they are muted, the remaining time and
how many offences they had in the last 24&nbsp;hours.

A new **Logs** button on the dashboard or the `/cm logs` command opens a
paginated view of recent chat logs. Each entry displays the player's head, a
snippet of the muted message and when it was recorded. These logs are stored in
`data/logs.json` and persist even if the server runs in offline mode. You can
remove all entries with the **Clear Logs** button or by running `/cm clearlogs`.
The `max-log-entries` option in `config.yml` controls how many of these entries
are kept (default `1000`). When the limit is exceeded, the oldest logs are
discarded automatically.
The `save-interval-ticks` option determines how often punishment and log files
are persisted (default `100` ticks).

Use `/cm reload` to re-read all configuration files. OpenAI options such as
`openai-key`, `model`, `threshold` and `rate-limit` are applied immediately and
event listeners are re-registered without restarting the server. Changes to the
`blocked-words` list are also detected automatically when `config.yml` is saved
so the filter updates without using this command.
