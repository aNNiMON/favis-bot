# favis-bot

Telegram bot for tagging stickers and other media and send them in inline mode. Supports multiple users with activation by the admin, but intended to be self-hosted for the admin and his close friends.

Tagging is performed in a web form. You can use a local machine for self-use or any server with shared IP and Java 11+ installed.


### Building

JDK 11 or newer required.

```bash
./gradlew shadowJar
```

### Running

Java 11 or newer required.

```bash
cp favisbot-example.yaml favisbot.yaml
# edit config: admin id, bot token, etc
vim favisbot.yaml
java -Dfile.encoding=UTF8 -cp favisbot.jar com.annimon.favisbot.FavisBot
```

SQLite database will be created after the first launch.
