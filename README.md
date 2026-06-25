# NoteBot — Minecraft 1.21.11 Fabric Mod

Автоматически проигрывает MIDI-файл на нотных блоках (`note_block`): поворачивает камеру игрока к нужному блоку и эмулирует ЛКМ в нужный момент.

## Стек

- **Minecraft:** 1.21.11
- **Загрузчик:** Fabric Loader 0.19.3+
- **API:** Fabric API 0.141.4+1.21.11
- **Mappings:** Yarn 1.21.11+build.1
- **Java:** 21+
- **Gradle:** 9.5.0
- **Loom:** 1.17.12

## Структура

```
notebot-mod/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── fabric.mod.json
├── README.md
└── src/main/
    ├── java/com/example/notebot/
    │   ├── NoteBotMod.java              — точка входа, регистрация событий/команд
    │   ├── config/NoteBotConfig.java    — GSON-конфиг
    │   ├── midi/
    │   │   ├── MidiParser.java          — парсер javax.sound.midi, корректно
    │   │   │                              учитывает tempo-change events
    │   │   └── MidiNoteEvent.java
    │   ├── playback/NotePlaybackManager.java
    │   │                                — расписание, поворот камеры, ЛКМ
    │   ├── command/NoteBotCommand.java  — /notebot play|stop|pause|resume|reload|status
    │   └── mixin/
    │       ├── ClientPlayerEntityAccessor.java — yaw/pitch сеттеры
    │       └── MinecraftClientAccessor.java    — invoker для private doAttack()
    └── resources/
        ├── fabric.mod.json
        └── notebot.mixins.json
```

## Сборка

```bash
./gradlew build          # собирает JAR в build/libs/
./gradlew runClient      # запускает клиент для отладки
```

JAR после сборки: `build/libs/notebot-mod-1.0.0.jar`.

## Установка

1. Установить Fabric Loader для 1.21.11 через [fabricmc.net](https://fabricmc.net).
2. Скачать Fabric API (тот же JAR, что и зависимость в `build.gradle`) и положить в `.minecraft/mods/`.
3. Положить собранный JAR мода туда же.
4. Запустить игру один раз — мод создаст `config/notebot/notebot.json` и папку.

## Конфигурация

Файл: `config/notebot/notebot.json`

```json
{
  "midiFile": "song.mid",
  "blocks": {
    "C": [10, 64, 10],
    "D": [11, 64, 10],
    "E": [12, 64, 10],
    "F": [13, 64, 10],
    "G": [14, 64, 10],
    "A": [15, 64, 10],
    "B": [16, 64, 10]
  },
  "noteMap": {
    "60": "C",
    "62": "D",
    "64": "E",
    "65": "F",
    "67": "G",
    "69": "A",
    "71": "B"
  },
  "clickDelayMs": 80,
  "maxReach": 4.5
}
```

### Поля

| Поле             | Тип          | Описание |
|------------------|--------------|----------|
| `midiFile`       | string       | MIDI-файл относительно `config/notebot/` |
| `blocks`         | объект       | `имя ноты → [x, y, z]` координаты нотных блоков |
| `noteMap`        | объект       | `MIDI-номер (как строка) или имя → имя блока`. Можно использовать только имена нот: `"C": "C"`, `"D": "D"` — тогда все MIDI-ноты семейства C попадут на блок C. |
| `clickDelayMs`   | int          | Минимальный интервал между кликами (мс). По умолчанию 80. |
| `maxReach`       | double       | Максимальная дистанция до блока, иначе нота пропускается. По умолчанию 4.5 (максимум vanilla survival). |

### MIDI

Положите свой `.mid` в `config/notebot/song.mid` (или укажите другое имя в `midiFile`).

Парсер:
- читает все треки (multi-track Type 1 поддерживается),
- учитывает tempo-change events (мета-события 0x51), поэтому время воспроизведения точное независимо от смен темпа,
- пропускает ноты с velocity = 0 (= NOTE_OFF),
- держит ноты длительностью < 5 мс как артефакты.

## Команды

| Команда              | Действие |
|----------------------|----------|
| `/notebot play`      | Начать (или продолжить с паузы). Также перечитывает конфиг и MIDI. |
| `/notebot pause`     | Поставить на паузу. |
| `/notebot resume`    | Продолжить (только если на паузе). |
| `/notebot stop`      | Остановить и сбросить позицию. |
| `/notebot reload`    | Перечитать `notebot.json` и MIDI. |
| `/notebot status`    | Текущее состояние. |

## Как это работает

1. На старте мод читает `notebot.json` и парсит MIDI.
2. На `play` запоминается `startTimeMs`.
3. На каждом `END_CLIENT_TICK` менеджер воспроизведения:
   - вычисляет `elapsed = now − startTimeMs`,
   - находит ноты с `timeMs ≤ elapsed`,
   - для каждой ноты: маппит MIDI-номер в имя блока → берёт координаты из конфига → поворачивает камеру игрока ровно в центр блока → вызывает `MinecraftClient.doAttack()` (через invoker-accessor).
4. Поворот камеры сделан через `@Accessor`-mixin (`ClientPlayerEntityAccessor`), потому что в Yarn 1.21+ у `Entity` нет публичного `setPitch()`.
5. ЛКМ сделан через `@Invoker`-mixin (`MinecraftClientAccessor`), потому что `doAttack()` приватный.
6. Один клик в тик максимум (`clickDelayMs` контролирует минимальный интервал), чтобы не получить волну C2S-пакетов и анти-чит-бан.

## Совместимость

- Только **client** (`environment: "client"`). На сервере работать не будет (нет entity-тиков).
- Singleplayer и multiplayer (при условии, что сервер не использует строгий anti-cheat типа NoCheatPlus с проверкой aim).

## Известные ограничения

- Расстояние до блоков ≤ 4.5 блока (vanilla survival). Если нужно дальше — увеличьте `maxReach`, но сервер может отклонить пакет.
- Для воспроизведения нот с очень высоким BPM может понадобиться уменьшить `clickDelayMs` (но ≥ 20).
- Поворот камеры мгновенный (teleport-aim) — это бот, не человек.

## Лицензия

MIT.