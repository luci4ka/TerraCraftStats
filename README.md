# 📊 TerraCraftStats — статистика игроков для Paper 1.21

**TerraCraftStats** — плагин для отслеживания и отображения статистики игроков с поддержкой MariaDB, мультисерверности, мультиязычности (8 языков) и системы интеграций (Vault, LuckPerms, PlaceholderAPI, TerraCraftCore).

[![Version](https://img.shields.io/github/v/release/luci4ka/PlayerStats)](https://github.com/luci4ka/PlayerStats/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21-blue.svg)](https://papermc.io)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21-green.svg)](https://papermc.io)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

---

## ✨ Возможности

### 📈 Статистика игроков

* ⏳ Время игры
* ⛏ Блоки (поставлено / сломано)
* ☠ PvE (мобы, **элитные мобы/боссы**, конкретные типы мобов)
* ⚔ PvP (убийства / смерти, K/D, killstreak, per-victim)
* 🏃 Дистанция (ходьба, бег, присед, лестницы, элитры, лодка, вагонетка)
* 💥 Урон (нанесено, получено, заблокировано)
* 🔨 Крафт (предметы, еда, зелья, торты)
* 🏹 Стрельба (стрелы, рыба)
* 🐾 Животные (приручение, разведение, стрижка)
* 🌐 Перемещение (телепорты, порталы)
* 💰 Баланс (Vault)
* 🎖 Группы (LuckPerms)
* ⚡ Действия (прыжки, сон, команды, чат, торговля, опыт, уровни)
* 🛡 Бой (щит, тотемы, дельфины)
* ⛏ Ресурсы (алмазы, незерит, урожай, таблички, кровати, ТНТ, снеговики)

**«Боссы»** в статистике PvE — это **элитные мобы**: Ender Dragon, Wither, Elder Guardian, Warden, а также любые мобы, у которых в кастомном имени есть ключевое слово из `tracking.mob-boss-keywords` (`Boss`, `Elite` и т.п.). Настраивается в `config.yml`.

### 🏆 Топы

* Основные категории: время, PvP, PvE, блоки, баланс
* **Топ по конкретной статистике** (`/top <stat>`)
* **Топ убийц конкретного игрока** (`/top pvp <ник>`)
* Кэш в памяти (обновление раз в 30 сек, без блокировки main-потока)
* **Top-chips** — значки топ-1 рядом с ником
* **Top-notifications** — уведомления о смене топа

### 📈 Sparkline

* Графики прогресса (`▁▂▃▅▇█`) в `/stats`
* За N дней (из `stat_history`)
* По категориям (kills, deaths, mobs, blocks, playtime)
* **Bar chart** в GUI (в подстраницах `/stats gui`)

### 🔗 Интеграции (система хуков)

* **TerraCraftCore** — AFK / god-mode (через TerraCoreAPI)
* **Vault** — экономика и баланс
* **PlaceholderAPI** — плейсхолдеры с кэшем (30 сек TTL, async fill)
* **bStats** — метрики

### 🎁 Дополнительные фичи

* **Мультиязычность** (8 языков: ru, en, uk, kk, be, uz, zh, es)
* **ConfigLib** — автообновление конфига с сохранением комментариев
* **Update Checker** — проверка обновлений через GitHub Releases
* **Discord Webhook** — уведомления о смене топа
* **Snapshot** — снимки статистики для истории (`stat_history`)
* **GUI-инвентарь** — `/stats gui` (с головой игрока, glow-табами, sparkline/bar-chart)
* **Sprite-спрайты** — ванильные иконки предметов/блоков в чате (1.21.11+)
* **Сравнение игроков** — `/compare <игрок>`
* **Rollback статистики** — `/stats rollback` (админ-команда)
* **Rainbow / Festive** — праздничные эффекты (модуль `cosmetics.yml`)
* **Bukkit Services API** — `PlayerStatsApi` + `PlayerStatsApiProvider`

### 🛡 Защита от накрутки

* Исключение игроков (`/exclude`) — не попадают в топы и PvP
* Игнорирование Creative / Spectator
* Игнорирование AFK (TerraCraftCore)
* Игнорирование GodMode (TerraCraftCore)
* PvP antifarm (cooldown)
* Лимит убийств одного игрока в день
* **Rollback-lock** — после rollback инкремент блокируется на N секунд

### 💾 База данных

* **MariaDB** (через MineSQL) — единственный поддерживаемый бэкенд
* **Миграции схемы** (`DatabaseMigrator` v0 → v3)
* Автоматическое создание таблиц
* Атомарные инкременты (`INSERT ... ON DUPLICATE KEY UPDATE`)
* Сохранение статистики при выключении сервера
* **История статистики** (`stat_history`) для графиков
* **Delta-журнал** (`stat_deltas`) для точного rollback (опционально)
* **NULL-семантика** в `value_int` / `value_double` / `value_string` — 0 отличим от «не записано»

### 🌍 Мультисерверность

* Общая статистика игрока
* Статистика по серверу
* Топы по серверам
* Отображаемые имена серверов

---

## 📋 Команды

### `/stats`

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/stats` | Показать свою статистику | `playerstats.stats` |
| `/stats <игрок>` | Статистика другого игрока | `playerstats.stats.others` |
| `/stats gui` | GUI со статистикой | `playerstats.stats` |
| `/stats pvp [игрок] [стр]` | PvP-статистика | `playerstats.stats` |
| `/stats mob [игрок] [стр]` | PvE-статистика | `playerstats.stats` |
| `/stats block [игрок] [стр]` | Статистика блоков | `playerstats.stats` |
| `/stats rollback <player> <stat> <value>` | Установить значение статы | `playerstats.rollback` |
| `/stats rollback <player> --all` | Обнулить все статы | `playerstats.rollback` |
| `/stats rollback <player> --from <date> <stat>` | Откат до даты (нужен `track-deltas`) | `playerstats.rollback` |

### `/top`

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/top` | Топы по основным категориям | `playerstats.top` |
| `/top <stat>` | Топ по конкретной статистике (autocomplete) | `playerstats.top` |
| `/top pvp <ник>` | Топ убийц конкретного игрока | `playerstats.top` |

### `/share` и `/compare`

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/share [игрок]` | Поделиться статистикой | `playerstats.share` |
| `/compare <игрок>` | Сравнить статистику | `playerstats.compare` |

### `/exclude`

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/exclude <игрок>` | Исключить из статистики | `playerstats.exclude` |
| `/exclude remove <игрок>` | Вернуть в статистику | `playerstats.exclude` |
| `/exclude list` | Список исключённых | `playerstats.exclude` |

### `/serverstats`

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/serverstats` | Статистика сервера | `playerstats.serverstats` |

### `/playerstats`

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/playerstats reload` | Перезагрузка конфига | `playerstats.reload` |
| `/playerstats snapshot` | Создать снапшот статистики | `playerstats.reload` |
| `/playerstats info` | Информация о плагине | `playerstats.info` |
| `/playerstats help` | Справка по командам | — |
| `/playerstats update` | Проверить обновления | `playerstats.update` |

---

## 📦 Установка

### Требования

* **Java 21+**
* **Paper 1.21+**
* **MariaDB 10.6+** (через [MineSQL](https://www.spigotmc.org/resources/minesql.92158/))
* **(опционально)** Vault, PlaceholderAPI, TerraCraftCore

### Шаги

1. Скачайте `PlayerStats-x.y.z.jar` из [Releases](https://github.com/luci4ka/PlayerStats/releases)
2. Поместите файл в `plugins/`
3. Установите и настройте **MineSQL** (`plugins/MineSQL/config.yml`) — источник `playerstats`
4. Перезапустите сервер
5. Настройте `plugins/PlayerStats/config.yml` и модули
6. Выполните `/playerstats reload`

---

## ⚙️ Конфигурация

### `config.yml` — основной (v6)

```yaml
config-version: 6
debug-level: 1
language: ru

enable-stat-sharing: true

modules:
  top-chips: true
  top-notifications: true
  sparkline: true
  discord: false
  cosmetics: false

database:
  id: playerstats         # ID источника данных в plugins/MineSQL/config.yml

multi-server:
  enabled: false
  server-name: "survival"

groups:
  default: "<white>Игрок"
  vip: "<gold>VIP"

pvp:
  per-victim-stats: true
  antifarm-cooldown-seconds: 300
  ignore-excluded-victims: true
  daily-limit-per-victim: 0

rollback:
  lock-seconds: 60
  notify-player: true
  log-to-console: true
  track-deltas: false

tracking:
  stats:
    play-time: true
    kills: true
    deaths: true
    mobs-killed: true
    blocks-placed: true
    blocks-broken: true
    distance-walked: true
    damage-dealt: true
    damage-taken: true
    items-crafted: true
    food-consumed: true
    arrows-shot: true
    fish-caught: true
    entities-tamed: true
    entities-bred: true
    teleports: true
    portal-uses: true
    logins: true
    killstreak: true
    best-killstreak: true
    # ... ~34 новых статы (jumps, sneak-time, sprint-time,
    #     elytra-distance, boat-distance, minecart-distance,
    #     damage-blocked, items-enchanted, items-broken,
    #     animals-sheared, villager-trades, crops-harvested,
    #     crops-planted, sleeps, commands-used, chat-messages,
    #     totems-used, experience-gained, levels-gained,
    #     potions-brewed, potions-drunk, signs-placed, beds-placed,
    #     tnt-ignited, cakes-eaten, snow-golems-built, diamonds-mined,
    #     netherite-mined, sprint-distance, sneak-distance,
    #     climbed-ladders, bells-rung, dolphins-fed)
  blocks:
    - "diamond_ore"
    - "ancient_debris"
  mob-boss-keywords:
    - "Boss"
    - "Elite"
```

### Модули

| Файл | Описание |
| :--- | :--- |
| `top-chips.yml` | Значки топ-1 |
| `top-notifications.yml` | Уведомления о смене топа |
| `sparkline.yml` | Графики прогресса |
| `discord.yml` | Discord Webhook |
| `cosmetics.yml` | Rainbow / Festive |

### `top-chips.yml` — пример

```yaml
enabled: true
refresh-seconds: 300
order:
  - "kills"
  - "mobs"
  - "blocks"
  - "playtime"
  - "balance"
chips:
  kills: "⚔"
  mobs: "☠"
  blocks: "⛏"
  playtime: "⌛"
  balance: "💰"
```

### `top-notifications.yml` — пример

```yaml
enabled: true
check-seconds: 300
only-first-place: false
send-to-discord: false
categories:
  kills: true
  mobs: true
  blocks: true
  playtime: true
  balance: true
messages:
  first-place: "👑 {player} обогнал {previous} в {category}!"
  top3-enter: "🏆 {player} вошёл в топ-3 {category} (#{rank})"
  top3-leave: "📉 {player} покинул топ-3 {category}"
  categories:
    kills: "убийствам"
    mobs: "мобам"
    blocks: "блокам"
    playtime: "времени"
    balance: "балансу"
```

### `sparkline.yml` — пример

```yaml
enabled: true
days: 7
show-in-stats: true
categories:
  kills: true
  deaths: true
  mobs: true
  blocks: true
  playtime: true
  balance: true
```

### `discord.yml` — пример

```yaml
enabled: false
webhook-url: "https://discord.com/api/webhooks/..."
username: "PlayerStats"
avatar-url: ""
rate-limit-seconds: 5
use-timestamp: true
mention: "none"
events:
  top-change: false
  rollback: true
```

### `cosmetics.yml` — пример

```yaml
rainbow:
  enabled: false
  apply-to:
    - "stats.title-self"
    - "top.header"

festive:
  enabled: false
  apply-to:
    - "stats.title-self"
```

---

## 🌍 Мультиязычность

**Поддерживаемые языки:**

* 🇷🇺 `ru` — русский
* 🇬🇧 `en` — английский
* 🇧🇾 `be` — беларуская
* 🇪🇸 `es` — español
* 🇰🇿 `kk` — қазақша
* 🇨🇳 `zh` — 简体中文
* 🇺🇦 `uk` — українська
* 🇺🇿 `uz` — o'zbekcha

**Выбор языка** — автоматически по языку клиента Minecraft:

* Точное совпадение (`ru_RU`)
* По языку (`ru` → `ru_*`)
* Fallback: СНГ → `ru`, иначе → `en`

**Добавить новый язык:**

1. Создайте `lang/<код>.yml` на основе `en.yml`.
2. Укажите `language: <код>` в `config.yml` (для консоли).
3. `/playerstats reload`.

**Формат** — MiniMessage (`<red>`, `<gold>`, `<gradient:...>`).

---

## 🔌 PlaceholderAPI

Плейсхолдеры регистрируются автоматически. **Кэш 30 сек + async fill** — SQL **никогда** не выполняется в `onRequest`.

**Префикс: `%ps_...%`**

### Базовые

| Плейсхолдер | Описание |
| :--- | :--- |
| `%ps_play_time%` | Время игры (форматированное: `2ч 5м`) |
| `%ps_play_time_seconds%` | Время игры (секунды) |
| `%ps_total_play_time%` | Время игры по всем серверам |
| `%ps_kills%` | Убийства |
| `%ps_deaths%` | Смерти |
| `%ps_kd%` | K/D |
| `%ps_killstreak%` | Текущая серия |
| `%ps_best_killstreak%` | Лучшая серия |
| `%ps_balance%` | Баланс (форматированный) |
| `%ps_balance_raw%` | Баланс (число) |
| `%ps_group%` | Группа |
| `%ps_blocks_placed%` | Поставлено блоков |
| `%ps_blocks_broken%` | Сломано блоков |
| `%ps_mobs_killed%` | Убито мобов |
| `%ps_bosses_killed%` | Убито **элитных мобов/боссов** |
| `%ps_distance_walked%` | Пройдено блоков |
| `%ps_damage_dealt%` | Нанесено урона |
| `%ps_damage_taken%` | Получено урона |
| `%ps_items_crafted%` | Скрафтено предметов |
| `%ps_food_consumed%` | Съедено еды |
| `%ps_arrows_shot%` | Выстрелов |
| `%ps_fish_caught%` | Поймано рыбы |
| `%ps_entities_tamed%` | Приручено животных |
| `%ps_entities_bred%` | Разведено животных |
| `%ps_teleports%` | Телепортов |
| `%ps_portal_uses%` | Использовано порталов |
| `%ps_logins%` | Входов |
| `%ps_jumps%` | Прыжков |
| `%ps_sneak_time%` | Время в приседе (сек) |
| `%ps_sprint_time%` | Время в беге (сек) |
| `%ps_diamonds_mined%` | Алмазов добыто |
| `%ps_netherite_mined%` | Незерита добыто |

### Специальные

| Плейсхолдер | Описание |
| :--- | :--- |
| `%ps_unique_players%` | Уникальных игроков на сервере |
| `%ps_server_name%` | Имя текущего сервера |
| `%ps_server_id%` | ID текущего сервера |

### Топы

| Плейсхолдер | Описание |
| :--- | :--- |
| `%ps_top_<stat>_<n>_name%` | Ник #n в топе по стате |
| `%ps_top_<stat>_<n>_value%` | Значение #n в топе по стате |
| `%ps_rank_<stat>%` | Позиция игрока в топе |

Примеры:

```
%ps_top_kills_1_name%      → Steve
%ps_top_kills_1_value%     → 152
%ps_top_balance_3_name%    → Alex
%ps_rank_kills%            → 5
```

**Кэш обновляется раз в 30 сек (или при invalidate из `StatsListener`).** Если данных ещё нет — вернёт пустую строку, следующий запрос (через ~1 сек) подтянет.

После установки:

```
/papi reload
```

---

## 💬 Discord Webhook

Уведомления в Discord (модуль `discord.yml`):

```yaml
enabled: true
webhook-url: "https://discord.com/api/webhooks/..."
username: "PlayerStats"
events:
  top-change: false
  rollback: false
```

---

## 🛡 Rollback статистики

Если игрок накрутил стату (баг, чит) — админ может откатить:

```
/stats rollback <player> <stat> <value>      # установить конкретное значение
/stats rollback <player> --all               # обнулить все int-статы
/stats rollback <player> --from <date> <stat>   # откат до даты (нужен track-deltas)
```

**Даты**: `2026-01-01`, `2026-01-01_12-00`, `1d`, `2h`, `30m`, `60s`.

**Rollback-lock**: после отката игрок **не** инкрементит стату N секунд (`rollback.lock-seconds`, по умолчанию 60).

**Право**: `playerstats.rollback`.

---

## 🧩 Java API

Плагин регистрирует `PlayerStatsApi` в Bukkit `ServicesManager`:

```java
PlayerStatsApi api = PlayerStatsApiProvider.get();
if (api == null) {
    getLogger().warning("PlayerStats не установлен");
    return;
}

int kills = api.getStat(player, "kills");
double balance = api.getStatDouble(player.getUniqueId(), "balance");
int rank = api.getRank(player.getUniqueId(), "kills");
List<LeaderboardEntry> top = api.getTop("kills", 10);
PlayerStatsSnapshot snapshot = api.getSnapshot(player.getUniqueId());
boolean excluded = api.isExcluded(player.getUniqueId());
```

**Подключение:** только `compileOnly` — не shade'ить.

---

## 🛠 Сборка

```bash
git clone https://github.com/luci4ka/PlayerStats.git
cd PlayerStats
./gradlew shadowJar
```

Готовый JAR: `build/libs/PlayerStats-1.0.jar`.

### Требования для сборки

* **JDK 21+**
* **Gradle** (включён в wrapper)

---

## 📁 Структура

```
src/main/java/ru/terracraft/playerstats/
├── api/
│   ├── PlayerStatsApi.java
│   ├── PlayerStatsApiProvider.java
│   ├── exception/PlayerStatsApiException.java
│   ├── impl/PlayerStatsApiImpl.java
│   ├── model/
│   │   ├── LeaderboardEntry.java
│   │   └── PlayerStatsSnapshot.java
│   └── event/
│       └── PlayerStatChangeEvent.java
├── core/
│   ├── commands/       # StatsCommand, TopCommand, RollbackCommand,
│   │                   # ShareCommand, CompareCommand, ServerStatsCommand,
│   │                   # ExcludeCommand, InfoCommand, ReloadCommand,
│   │                   # HelpCommand, PlayerStatsCommand, CommandRegistrar
│   ├── config/         # ConfigManager, LanguageManager, TrackedBlocks
│   │   └── model/      # MainConfig, TopChipsConfig, TopNotificationsConfig,
│   │                   # SparklineConfig, DiscordConfig, CosmeticsConfig
│   ├── database/
│   │   ├── dao/        # PlayerDAO, PlayerStatsDAO, ServerDAO,
│   │   │               # ServerStatsDAO, StatTypeDAO, StatHistoryDAO,
│   │   │               # LeaderboardDAO, TotalServerDAO
│   │   ├── DatabaseMigrator.java
│   │   ├── MigrationStep.java
│   │   └── MigrationUtils.java
│   ├── gui/            # StatsGui, StatsGuiListener
│   ├── listeners/      # StatsListener, JoinListener, ListenerRegistrar
│   ├── multithreading/ # ThreadManager
│   ├── pvp/            # PvPTracker
│   ├── services/       # PlayerGroupService, TopCache, TopChangeNotifier,
│   │                   # RollbackLockService, StatSnapshotTask, UpdateChecker
│   └── utils/          # FormatUtil, TimeUtil, Sparkline, SpriteUtil
├── hooks/              # Hook, HooksManager, VaultHook, PlaceholderAPIHook,
│                       # PlayerStatsExpansion, TerraCoreBridge
└── PlayerStatsPlugin.java

src/main/resources/
├── config.yml
├── plugin.yml
├── top-chips.yml
├── top-notifications.yml
├── sparkline.yml
├── discord.yml
├── cosmetics.yml
└── lang/
    ├── ru.yml
    ├── en.yml
    ├── es.yml
    ├── zh.yml
```

---

## 🐛 Известные проблемы

| Проблема | Решение |
| :--- | :--- |
| `--from` возвращает то же значение (no-op) | Включите `config.yml` → `rollback.track-deltas: true` |
| `%ps_top_*%` возвращает пустую строку | Подождите 1–2 сек после старта: `TopCache` заполняется cron'ом |
| Спрайты не отображаются в чате | Клиент < 1.21.9 — сработает fallback-эмодзи |
| `serverName` изменён, но статистика пишется в старый ID | Перезапустите сервер (`StatsListener.serverId` — `final`) |
| `placeholders` показывают `0` при первом запросе | Норма — кэш PAPI заполняется в async; следующий запрос через 1 сек вернёт данные |

---

## 📄 Лицензия

MIT