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
Set `language` to `en` or `tr` to change plugin messages.
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
