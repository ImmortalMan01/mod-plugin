# ChatModeration

A simple Paper/Spigot plugin that integrates with OpenAI Moderation API to automatically mute players for inappropriate chat messages. The plugin demonstrates asynchronous HTTP communication, rate limiting and basic punishment storage.

## Building
Requires JDK 21. If the Gradle wrapper JAR is missing, run `gradle wrapper` or use your system Gradle.

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
The `model` option defaults to OpenAI's `omni-moderation-latest`, but you may set it to any supported model. When `gpt-4.1-mini` or `gpt-4.1` is selected the plugin will use the chat completion API with a system prompt to simply answer whether the message contains profanity.
You can customize this system prompt via the `chat-prompt` option if you need different wording.
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
containing one of these words will be muted without an API call. Set `use-blocked-words`
to `false` to disable this list-based filter and rely solely on the OpenAI model.
The filter normalizes text when matching, converting Turkish letters like
`ş`, `ö`, `ç`, `ğ`, `ı` and `ü` to their ASCII equivalents and removing other
diacritics. Punctuation is converted to spaces so word boundaries are kept.
Each token is checked against the block list and consecutive single-letter
tokens are combined, allowing `s i k` to match a blocked word of `sik` while

### GUI Customization
A separate `gui.yml` file controls the layout of the `/cm gui` dashboard. You can edit
inventory sizes, button slots and the material/name for each menu item. Example:

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
      material: BOOK_AND_QUILL
      name: "&eView Logs"
      action: logs
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

A new **Logs** button on the dashboard opens a paginated view of recent chat
logs. Each entry displays the player's head, a snippet of the muted message and
when it was recorded. These logs are stored in `data/logs.json` and persist even
if the server runs in offline mode.

Use `/cm reload` to re-read all configuration files. OpenAI options such as
`openai-key`, `model`, `threshold` and `rate-limit` are applied immediately and
event listeners are re-registered without restarting the server.
