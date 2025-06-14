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
The plugin generates `config.yml` on first run. Insert your OpenAI API key and adjust thresholds or punishments as needed.
Set `language` to `en` or `tr` to change plugin messages. The selected language file (`messages_en.yml` or `messages_tr.yml`) will be copied to the plugin folder so you can edit any text.
The service now uses OpenAI's `omni-moderation-latest` model by default.
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
You can also define `blocked-words` for custom profanity detection. Any chat message
containing one of these words will be muted without an API call. Set `use-blocked-words`
to `false` to disable this list-based filter and rely solely on the OpenAI model.

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

Reload both `config.yml` and `gui.yml` with `/cm reload`.
