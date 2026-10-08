# 📊 PlayerStats — статистика игроков для Paper 1.21

**PlayerStats** — плагин для отслеживания и отображения статистики игроков с поддержкой MySQL/SQLite, HTTP API с аутентификацией, мультисерверности, мультиязычности (8 языков) и множества интеграций.

[![Version](https://img.shields.io/github/v/release/luci4ka/PlayerStats)](https://github.com/luci4ka/PlayerStats/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21-blue.svg)](https://papermc.io)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21-green.svg)](https://papermc.io)
[![Web API](https://img.shields.io/badge/API-HTTP-orange.svg)](#-http-api)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

---

## ✨ Возможности

### 📈 Статистика игроков

* ⏳ Время игры
* ⛏ Блоки (поставлено / сломано)
* ☠ PvE (мобы, боссы, конкретные типы мобов)
* ⚔ PvP (убийства / смерти, K/D, killstreak, per-victim)
* 🏃 Дистанция, урон, крафт, еда, стрельба, рыбалка
* 🐾 Животные (приручение, разведение)
* 🌐 Телепорты, порталы
* 💰 Баланс (Vault)
* 🎖 Группы (LuckPerms)

### 🌐 HTTP API

* Встроенный HTTP-сервер (`com.sun.net.httpserver`)
* JSON-ответы
* **Аутентификация по API-ключу** (`X-API-Key` или `Authorization: Bearer`)
* **Автогенерация ключа** при первом запуске
* **CORS** для браузерных клиентов
* **Rate limiting** (запросов в минуту)
* **Public routes** (без ключа)
* Настраиваемый хост и порт
* Модуль: `api.yml`

### 🏆 Топы

* Время, PvP, PvE, блоки, баланс, killstreak, дистанция, урон, крафт, рыбалка, животные, телепорты, входы
* **Топ по конкретной статистике** (`/top <stat>`)
* **Топ убийц конкретного игрока** (`/top pvp <ник>`)
* Кэширование топов
* Форматирование значений
* Кликабельные имена игроков
* **Top-chips** — значки топ-1 рядом с ником
* **Top-notifications** — уведомления о смене топа

### 🏆 Достижения (Achievements)

* **Именованные достижения** с иконкой, категорией и описанием
* GUI с прогрессом (`/stats gui` → вкладка «🏆 Достижения»)
* **Награды за достижения** — предметы, деньги, команды, звуки, Discord
* **Прогресс-бары** в lore
* Дата получения
* **Временные достижения** (`reset: daily | weekly | monthly`) — сброс прогресса
* **Повторяемые награды** (`repeat-reward: true`)
* **8 языков** локализации

### 🎁 Milestones

* **Числовые вехи** (`100 mobs`, `1000 blocks`)
* Награды от консоли
* Discord-уведомления

### 📈 Sparkline

* Графики прогресса (`▁▂▃▅▇█`) в `/stats`
* За N дней
* По категориям

### 🔗 Интеграции (система хуков)

* **LuckPerms** — группы игроков
* **Vault** — экономика
* **EssentialsX** — AFK и GodMode
* **PlaceholderAPI** — плейсхолдеры с кэшем
* **DecentHolograms** — голограммы с топами
* **bStats** — метрики

### 🎁 Дополнительные фичи

* **Мультиязычность** (8 языков: ru, en, be, es, kk, zh, uk, uz)
* **Config Updater** — автообновление конфига с сохранением комментариев
* **Config Validator** — валидация конфига при загрузке (откат на дефолт при ошибке)
* **Update Checker** — проверка новых версий через GitHub Releases
* **Discord Webhook** — уведомления о достижениях, вехах, топ-смене, rollback
* **Свои голограммы** (`/playerstats hologram`, модуль `holograms.yml`)
* **Snapshot** — снимки статистики для истории
* **GUI-инвентарь** — `/stats gui`
* **Сравнение игроков** — `/compare <игрок>`
* **Rollback статистики** — `/stats rollback` (админ-команда)
* **Автобэкап БД** — SQLite (`VACUUM INTO` + gzip) или MySQL (`mysqldump` + gzip)
* **Rainbow / Festive** — праздничные эффекты (модуль `cosmetics.yml`)

### 🛡 Защита от накрутки

* Исключение игроков (`/exclude`) — не попадают в топы и PvP
* Игнорирование Creative / Spectator
* Игнорирование AFK (EssentialsX)
* Игнорирование GodMode
* Защита от снарядов (стрелы, зелья, трезубцы)
* PvP antifarm (cooldown)
* Лимит убийств одного игрока в день
* **Rollback-lock** — после rollback инкремент блокируется на N секунд

### 💾 База данных

* **MySQL** (HikariCP)
* **SQLite** (HikariCP + WAL-режим)
* **Миграции схемы** (`DatabaseMigrator` v0 → v3)
* Автоматическое создание таблиц
* Атомарные инкременты (`INSERT ... ON DUPLICATE KEY UPDATE`)
* Сохранение статистики при выключении сервера
* **История статистики** (`stat_history`) для графиков
* **Delta-журнал** (`stat_deltas`) для точного rollback (опционально)

### 💾 Автобэкапы БД

* **SQLite**: `VACUUM INTO` + gzip (консистентный бэкап с WAL)
* **MySQL**: `mysqldump` + gzip
* Хранение последних N бэкапов
* **Бэкап перед миграцией** (`before-migration: true`)
* Восстановление через `/playerstats backup restore <file>` (SQLite)
* Модуль: `backup.yml`

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
| `/stats achievements` | GUI достижений | `playerstats.stats` |
| `/stats pvp [игрок] [стр]` | PvP-статистика | `playerstats.stats` |
| `/stats mob [игрок] [стр]` | PvE-статистика | `playerstats.stats` |
| `/stats block [игрок] [стр]` | Статистика блоков | `playerstats.stats` |
| `/stats rollback <player> <stat> <value>` | Установить значение статы | `playerstats.rollback` |
| `/stats rollback <player> --all <value>` | Обнулить все статы | `playerstats.rollback` |
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
| `/playerstats hologram create <stat>` | Создать голограмму | `playerstats.reload` |
| `/playerstats hologram remove <stat>` | Удалить голограмму | `playerstats.reload` |
| `/playerstats hologram list` | Список голограмм | `playerstats.reload` |
| `/playerstats backup` | Создать бэкап БД сейчас | `playerstats.backup` |
| `/playerstats backup list` | Список бэкапов | `playerstats.backup` |
| `/playerstats backup restore <file>` | Восстановить БД из бэкапа | `playerstats.backup` |
| `/playerstats info` | Информация о плагине | `playerstats.info` |
| `/playerstats help` | Справка по командам | — |
| `/playerstats update` | Проверить обновления | `playerstats.update` |
| `/playerstats apikey` | Показать API-ключ | `playerstats.apikey` |
| `/playerstats apikey regenerate` | Перегенерировать API-ключ | `playerstats.apikey` |

---

## 🔌 HTTP API

### Настройка (`api.yml`)

```yaml
enabled: true
host: "0.0.0.0"
port: 8080

auth:
  auto-generate-key: true
  api-key: ""                       # если пусто — генерируется при старте
  log-key-on-startup: true
  header: "X-API-Key"
  bearer-prefix: "Bearer "
  public-routes:
    - "/api/health"
    - "/api/version"

cors:
  enabled: false
  allow-origin: "*"
  allow-methods: "GET, OPTIONS"
  allow-headers: "Content-Type, X-API-Key, Authorization"

rate-limit:
  enabled: true
  requests-per-minute: 60
```

### Аутентификация

**По умолчанию API защищён** — все роуты, кроме `public-routes`, требуют **API-ключ**.

**Передача ключа:**

```http
GET /api/player/Steve
X-API-Key: psk_3a7f9b2c8e1d4f6a5b0c9e2f8d7a1b3c
```

Или:

```http
GET /api/player/Steve
Authorization: Bearer psk_3a7f9b2c8e1d4f6a5b0c9e2f8d7a1b3c
```

**Просмотр ключа:** `/playerstats apikey`.
**Перегенерация:** `/playerstats apikey regenerate`.

### Эндпоинты

| Метод | Путь | Описание | Auth |
| :--- | :--- | :--- | :--- |
| GET | `/api/health` | Проверка БД + API | 🌐 public |
| GET | `/api/version` | Версия плагина и API | 🌐 public |
| GET | `/api/server` | Статистика текущего сервера | 🔒 |
| GET | `/api/server/{id}` | Статистика конкретного сервера | 🔒 |
| GET | `/api/server/total` | Суммарная статистика по всем серверам | 🔒 |
| GET | `/api/player/{id}` | Статистика игрока (ID / UUID / имя) | 🔒 |
| GET | `/api/player/{id}/achievements` | Достижения игрока | 🔒 |
| GET | `/api/player/{id}/history/{stat}?days=30` | История статы | 🔒 |
| GET | `/api/top?stat=play_time&limit=10` | Топ игроков | 🔒 |

### Примеры

**`GET /api/health`** (public):

```json
{
  "status": "ok",
  "database": true,
  "api": true,
  "uptime_ms": 123456789
}
```

**`GET /api/player/Steve`**:

```json
{
  "player": "Steve",
  "play_time": 360000,
  "blocks_placed": 15000,
  "blocks_broken": 8000,
  "mobs_killed": 5000,
  "bosses_killed": 50,
  "kills": 120,
  "deaths": 80,
  "balance": 50000.0,
  "group": "VIP"
}
```

**`GET /api/top?stat=play_time&limit=3`**:

```json
{
  "Notch": 360000,
  "jeb_": 180000,
  "Dinnerbone": 90000
}
```

### CORS

Если **веб-панель** работает с **другого** домена:

```yaml
api:
  cors:
    enabled: true
    allow-origin: "https://panel.example.com"
```

### Rate limiting

```yaml
api:
  rate-limit:
    enabled: true
    requests-per-minute: 60
```

**Лимит** — **на IP**. **При** превышении — **429 Too Many Requests**.

---

## 📦 Установка

### Требования

* **Java 21+** (рекомендуется Java 21 для Paper 1.21)
* **Paper 1.21+**

### Шаги

1. Скачайте `PlayerStats-x.y.z.jar` из [Releases](https://github.com/luci4ka/PlayerStats/releases)
2. Поместите файл в `plugins/`
3. Перезапустите сервер
4. Настройте `plugins/PlayerStats/config.yml` и модули
5. Выполните `/playerstats reload`

---

## ⚙️ Конфигурация

### `config.yml` — основной (v4)

```yaml
config-version: 4
debug-level: 1
language: ru

modules:
  top-chips: true
  top-notifications: true
  sparkline: true
  holograms: true
  achievements: true
  milestones: true
  backup: true
  api: false
  discord: false
  cosmetics: false

database:
  type: sqlite
  host: localhost
  port: 3306
  name: playerstats
  user: root
  password: password

sqlite:
  file: playerstats.db

multi-server:
  enabled: false
  server-name: "survival"

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
    distance_walked: true
    damage_dealt: true
    damage_taken: true
    killstreak: true
    best_killstreak: true
    items_crafted: true
    food_consumed: true
    arrows_shot: true
    fish_caught: true
    entities_tamed: true
    entities_bred: true
    teleports: true
    portal_uses: true
    logins: true
  blocks:
    - "diamond_ore"
    - "ancient_debris"
    # ...
  mob-boss-keywords:
    - "Босс"
    - "Boss"
    - "Elite"
```

### Модули

| Файл | Описание |
| :--- | :--- |
| `achievements.yml` | Достижения (постоянные + временные) |
| `milestones.yml` | Вехи |
| `top-chips.yml` | Значки топ-1 |
| `top-notifications.yml` | Уведомления о смене топа |
| `sparkline.yml` | Графики прогресса |
| `holograms.yml` | Голограммы топов |
| `backup.yml` | Автобэкап БД |
| `api.yml` | HTTP API |
| `discord.yml` | Discord Webhook |
| `cosmetics.yml` | Rainbow / Festive |

### `achievements.yml` — пример

```yaml
achievements:
  # Постоянное достижение
  first_kill:
    icon: IRON_SWORD
    stat: kills
    threshold: 1
    category: pvp
    broadcast: true
    commands:
      - "give %player% diamond 1"

  # Временное (сброс каждый день)
  daily_killer_10:
    icon: DIAMOND_SWORD
    stat: kills
    threshold: 10
    category: pvp
    reset: daily                # daily | weekly | monthly
    repeat-reward: false        # выдавать награду повторно?
    broadcast: true
    commands:
      - "give %player% iron_ingot 5"
```

### `backup.yml` — пример

```yaml
enabled: true
interval-hours: 6
initial-delay-minutes: 1
keep-count: 14
folder: "backups"
filename-format: "yyyy-MM-dd_HH-mm-ss"
log-each-backup: true
before-migration: true
gzip: true
mysqldump-path: ""              # для MySQL
```

### `discord.yml` — пример

```yaml
enabled: false
webhook-url: "https://discord.com/api/webhooks/..."
username: "PlayerStats"
avatar-url: ""
rate-limit-seconds: 5
events:
  achievement-unlock: true
  milestone: true
  top-change: false
  rollback: false
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

## 🎁 Награды за достижения

При получении достижения можно **автоматически** выдать игроку:

* **предмет**,
* **деньги** (Vault),
* **команду** от консоли,
* **сообщение** в чат / в Discord,
* **звук**.

### Синтаксис

```yaml
achievements:
  killer_100:
    icon: NETHERITE_SWORD
    stat: kills
    threshold: 100
    category: pvp
    commands:
      - "give %player% netherite_sword 1"
      - "eco give %player% 5000"
    broadcast: true
    # Временное (опционально):
    reset: null                 # daily | weekly | monthly
    repeat-reward: false
```

### Плейсхолдеры в командах

| Плейсхолдер | Значение |
| :--- | :--- |
| `%player%` | Ник игрока |
| `%uuid%` | UUID игрока |

---

## 📄 `milestones.yml` — пример

```yaml
milestones:
  mobs_killed:
    "100":
      message: "<gold>🏆 Вы убили 100 мобов!"
    "1000":
      message: "<gold>🏆 Вы убили 1000 мобов!"
  blocks_placed:
    "5000":
      message: "<gold>🏆 Вы поставили 5000 блоков!"
```

**Плейсхолдеры**: `%player%`.

---

## 📄 `holograms.yml` — пример

```yaml
enabled: true
update-interval-seconds: 300
initial-delay-seconds: 60
top-size: 10
empty-line: "§8— — — — — —"

list:
  top-kills:
    enabled: true
    stat: kills
    title: "§6§l★ ТОП УБИЙСТВ ★"
    location:
      world: world
      x: 100.5
      y: 65.0
      z: 200.5
```

**Требует**: DecentHolograms.

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

**Выбор языка** — **автоматически** по **языку** **клиента Minecraft**:

* **Точное** совпадение (`ru_RU`)
* **По** **языку** (`ru` → `ru_*`)
* **Fallback**: **СНГ** → `ru`, **иначе** → `en`

**Добавить новый язык:**

1. Создайте `lang/<код>.yml` на основе `en.yml`.
2. Укажите `language: <код>` в `config.yml` (**для** **консоли**).
3. `/playerstats reload`.

**Формат** — **MiniMessage** (`<red>`, `<gold>`, `<gradient:...>`).

**Валидация**: при загрузке `lang/*.yml` проверяются **обязательные** **ключи**. **Если** чего-то не хватает — **warnings** в логе, но плагин работает.

---

## 🔌 PlaceholderAPI

Плейсхолдеры регистрируются автоматически с **кэшем** (обновление раз в 5 сек).

**Префикс: `%ps_...%`**

### Базовые

| Плейсхолдер | Описание |
| :--- | :--- |
| `%ps_play_time%` | Время игры (форматированное: `2ч 5м`) |
| `%ps_play_time_raw%` | Время игры (секунды) |
| `%ps_play_time_formatted%` | Время игры (`2д 2ч 5м`) |
| `%ps_kills%` | Убийства |
| `%ps_kills_formatted%` | Убийства (`1.2K`) |
| `%ps_deaths%` | Смерти |
| `%ps_kdr%` | K/D |
| `%ps_killstreak%` | Текущая серия |
| `%ps_best_killstreak%` | Лучшая серия |
| `%ps_balance%` | Баланс (сырое) |
| `%ps_balance_formatted%` | Баланс (`1,234.50`) |
| `%ps_group%` | Группа |
| `%ps_blocks_placed%` | Поставлено блоков |
| `%ps_blocks_broken%` | Сломано блоков |
| `%ps_mobs_killed%` | Убито мобов |
| `%ps_bosses_killed%` | Убито боссов |
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

### `_formatted`

**Любая** метрика **имеет** `_formatted`-вариант:

* `%ps_kills_formatted%` → `1.2K`
* `%ps_mobs_killed_formatted%` → `12.3K`
* `%ps_balance_formatted%` → `1,234.50`
* `%ps_play_time_formatted%` → `2д 2ч 5м`

### Top-chips и sparkline

| Плейсхолдер | Описание |
| :--- | :--- |
| `%ps_top_kills%` | Значок топ-1 (kills) |
| `%ps_top_chips%` | Все значки игрока |
| `%ps_top_count%` | Количество значков |
| `%ps_sparkline_kills%` | График прогресса |

### Динамические

| Плейсхолдер | Описание |
| :--- | :--- |
| `%ps_mob_killed_zombie%` | Убийства конкретного моба |
| `%ps_block_placed_diamond_block%` | Поставлено конкретного блока |
| `%ps_block_broken_diamond_ore%` | Сломано конкретного блока |
| `%ps_pvp_kill_<uuid>%` | Убийства конкретного игрока |
| `%ps_pvp_total%` | Всего PvP-убийств |
| `%ps_pvp_unique%` | Уникальных жертв |

### Достижения

| Плейсхолдер | Описание |
| :--- | :--- |
| `%ps_achievement_reset_in_<id>%` | Время до сброса (`3ч 12м` / `сброшено` / `—`) |

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
  achievement-unlock: true
  milestone: true
  top-change: false
  rollback: false
```

---

## 🛡 Rollback статистики

**Если** игрок **накрутил** стату (**баг**, **чит**) — админ может **откатить**:

```
/stats rollback <player> <stat> <value>    # установить конкретное значение
/stats rollback <player> --all <value>     # обнулить все int-статы
/stats rollback <player> --from <date> <stat>   # откат до даты (нужен track-deltas)
```

**Даты**: `2026-01-01`, `2026-01-01_12-00`, `1d`, `2h`, `30m`, `60s`.

**Rollback-lock**: после отката игрок **не** **инкрементит** стату **N** секунд (`rollback.lock-seconds`, по умолчанию 60).

**Право**: `playerstats.rollback`.

---

## 💾 Автобэкапы БД

**Автоматические** бэкапы **раз** в N часов (модуль `backup.yml`):

* **SQLite**: `VACUUM INTO` (консистентный бэкап с WAL) + gzip.
* **MySQL**: `mysqldump` + gzip.
* Хранение последних N бэкапов.
* **Бэкап перед миграцией** БД (`before-migration: true`).

**Ручное** управление:

```
/playerstats backup              # создать сейчас
/playerstats backup list         # список бэкапов
/playerstats backup restore <file>   # восстановить (только SQLite)
```

**Право**: `playerstats.backup`.

---

## 🛠 Сборка

```bash
git clone https://github.com/luci4ka/PlayerStats.git
cd PlayerStats
./gradlew shadowJar
```

Готовый JAR: `build/libs/PlayerStats-1.0.jar`.

### Требования для сборки

* **JDK 21+** (для Paper 1.21)
* **Gradle 9.1.0+** (включён в wrapper)

---

## 📁 Структура

```
src/main/java/com/playerstats/
├── api/
│   ├── http/                # HTTP API
│   ├── java/                # Java API + events
│   │   ├── PlayerStatsApi.java
│   │   ├── impl/PlayerStatsApiImpl.java
│   │   └── event/
│   │       ├── PlayerStatChangeEvent.java
│   │       ├── PlayerAchievementUnlockEvent.java
│   │       ├── PlayerAchievementResetEvent.java
│   │       └── PlayerMilestoneEvent.java
│   ├── ApiKeyManager.java
│   └── Handler/
├── core/
│   ├── achievements/       # Achievement, AchievementManager
│   ├── commands/           # StatsCommand, TopCommand, RollbackCommand, BackupCommand, ...
│   ├── config/             # ConfigHandler, LanguageManager, ModuleConfig, ConfigValidator, TrackedBlocks
│   ├── database/           # MySQL, SQLite, DAO, DatabaseMigrator, DatabaseBackupTask
│   ├── gui/                # StatsGui, AchievementsGui, Listeners
│   ├── listeners/          # StatsListener, ListenerRegistrar
│   ├── multithreading/     # ThreadManager
│   ├── pvp/                # PvPTracker
│   ├── services/           # PlayerGroupService, TopCache, UpdateChecker,
│   │                       # MilestoneManager, DiscordWebhook, HologramManager,
│   │                       # ServerStatsService, TopChangeNotifier, StatSnapshotTask,
│   │                       # RollbackLockService
│   └── utils/              # FormatUtil, TimeUtil, Sparkline, ProgressBar
├── hooks/                  # Хуки плагинов
└── Main.java

src/main/resources/
├── config.yml              # v4
├── plugin.yml
├── achievements.yml
├── milestones.yml
├── top-chips.yml
├── top-notifications.yml
├── sparkline.yml
├── holograms.yml
├── backup.yml
├── api.yml
├── discord.yml
├── cosmetics.yml
└── lang/
    ├── ru.yml
    ├── en.yml
    ├── be.yml
    ├── es.yml
    ├── kk.yml
    ├── zh.yml
    ├── uk.yml
    └── uz.yml
```

---

## 🐛 Известные проблемы

| Проблема | Решение |
| :--- | :--- |
| SQLite медленный при больших данных | Используйте MySQL |
| API без reverse proxy | Используйте Nginx / HTTPS |
| `--from` требует `track-deltas: true` | Включите в `config.yml` → `rollback.track-deltas` |
| Restore для MySQL не автоматический | Используйте `mysql -u ... < backup.sql` |
| Holograms обновляются раз в N секунд | Настройте `holograms.yml` → `update-interval-seconds` |

---

## 📄 Лицензия

MIT