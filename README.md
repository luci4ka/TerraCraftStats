# 📊 PlayerStats — статистика игроков для Paper 1.21

**PlayerStats** — плагин для отслеживания и отображения статистики игроков с поддержкой MySQL/SQLite, HTTP API, мультисерверности, мультиязычности и множества интеграций.

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
* ☠ PvE (мобы, боссы)
* ⚔ PvP (убийства / смерти, K/D)
* 💰 Баланс (Vault)
* 🎖 Группы (LuckPerms)
* 🔨 Jobs Reborn (заработок, уровни профессий)

### 🌐 HTTP API

* Встроенный HTTP-сервер (JDK `com.sun.net.httpserver`)
* JSON-ответы
* Настраиваемый хост и порт

### 🏆 Топы

* Время, PvP, PvE, блоки, баланс
* Кэширование топов (обновление раз в 30 секунд)
* Форматирование значений
* Кликабельные имена игроков

### 🔗 Интеграции (система хуков)

* **LuckPerms** — группы игроков
* **Vault** — экономика
* **EssentialsX** — AFK и GodMode
* **PlaceholderAPI** — плейсхолдеры с кэшем
* **DecentHolograms** — голограммы с топами
* **Jobs Reborn** — заработок и уровни профессий
* **bStats** — метрики

### 🎁 Дополнительные фичи

* **Мультиязычность** (`lang/ru.yml`, `lang/en.yml`)
* **Config Updater** — автоматическое обновление конфига с сохранением комментариев
* **Update Checker** — проверка новых версий через GitHub Releases
* **Milestones** — награды за достижения (100 мобов, 1000 блоков и т.д.)
* **Discord Webhook** — уведомления о достижениях в Discord
* **GUI-инвентарь** — `/stats gui` с иконками статистики
* **Сравнение игроков** — `/compare <игрок>`

### 🛡 Защита от накрутки

* Исключение игроков (`/exclude`)
* Игнорирование Creative / Spectator
* Игнорирование AFK (EssentialsX)
* Игнорирование GodMode
* Защита от снарядов (стрелы, зелья, трезубцы)

### 💾 База данных

* **MySQL** (HikariCP)
* **SQLite** (HikariCP + WAL-режим)
* Автоматическое создание таблиц
* Атомарные инкременты (`INSERT ... ON DUPLICATE KEY UPDATE`)
* Сохранение статистики при выключении сервера

### 🌍 Мультисерверность

* Общая статистика игрока
* Статистика по серверу
* Топы по серверам
* Отображаемые имена серверов

---

## 📋 Команды

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/stats` | Показать свою статистику | `playerstats.stats` |
| `/stats <игрок>` | Показать статистику другого игрока | `playerstats.stats.others` |
| `/stats gui` | Открыть GUI со статистикой | `playerstats.stats` |
| `/top` | Показать топы | `playerstats.top` |
| `/share [игрок]` | Поделиться статистикой | `playerstats.share` |
| `/compare <игрок>` | Сравнить статистику | `playerstats.compare` |
| `/exclude <игрок> [remove\|list]` | Управление исключениями | `playerstats.exclude` |
| `/playerstats reload` | Перезагрузка конфига | `playerstats.reload` |
| `/playerstats info` | Информация о плагине | `playerstats.info` |
| `/playerstats update` | Проверить обновления | `playerstats.update` |

---

## 🔌 HTTP API

Включается в `config.yml`:

```yaml
api:
  enabled: true
  host: "0.0.0.0"
  port: 8080
```

> ⚠ API не имеет встроенной аутентификации.
> Рекомендуется ограничить доступ через firewall или reverse proxy (Nginx).

### Эндпоинты

| Метод | Путь | Описание |
| :--- | :--- | :--- |
| GET | `/api/server/{serverId}` | Статистика сервера |
| GET | `/api/total` | Общая статистика |
| GET | `/api/player/{playerId}` | Статистика игрока |
| GET | `/api/top/{stat}/{limit}` | Топ игроков |
| GET | `/api/health` | Проверка БД |

### Примеры

**`/api/server/1`**

```json
{
  "success": true,
  "data": {
    "uniquePlayers": 42,
    "play_time": 360000,
    "blocks_placed": 15000,
    "blocks_broken": 8000,
    "mobs_killed": 5000,
    "bosses_killed": 50,
    "kills": 120,
    "deaths": 80
  }
}
```

**`/api/top/play_time/3`**

```json
{
  "success": true,
  "data": {
    "Notch": 360000,
    "jeb_": 180000,
    "Dinnerbone": 90000
  }
}
```

---

## 📦 Установка

### Требования

* **Java 21+** (рекомендуется Java 21 для Paper 1.21.11)
* **Paper 1.21+**

### Шаги

1. Скачайте `PlayerStats-1.0.jar` из [Releases](https://github.com/luci4ka/PlayerStats/releases)
2. Поместите файл в `plugins/`
3. Перезапустите сервер
4. Настройте `plugins/PlayerStats/config.yml`
5. Выполните `/playerstats reload`

---

## ⚙️ Конфигурация (`config.yml`)

```yaml
config-version: 2
debug-level: 1
language: ru

database:
  type: sqlite
  host: localhost
  port: 3306
  name: playerstats
  user: root
  password: "your_password"

sqlite:
  file: playerstats.db

multi-server:
  enabled: false
  server-name: default

server-names:
  default: "§6Основной сервер"
  survival: "§aSurvival"

api:
  enabled: false
  host: "0.0.0.0"
  port: 8080

groups:
  default: "§7Игрок"
  vip: "§aVIP"
  admin: "§cАдминистратор"

stats-click-commands:
  blocks: "/top blocks"
  pve: "/top pve"
  time: "/top time"
  pvp: "/top pvp"
  balance: "/balance"
  group: "/groups"
  server: "/server info"

mob-boss-keywords:
  - "Босс"
  - "Boss"
  - "Elite"

# Milestones — награды за достижения
milestones:
  enabled: false
  mobs_killed:
    100:
      message: "<gold>🏆 Вы убили 100 мобов!"
      commands:
        - "give %player% diamond 1"
  play_time:
    3600:
      message: "<gold>🏆 Вы играете уже 1 час!"
      commands:
        - "give %player% golden_apple 1"

# Discord Webhook
discord:
  enabled: false
  webhook-url: "https://discord.com/api/webhooks/..."
  username: "PlayerStats"
  avatar-url: ""
  events:
    milestone: true
    level-up: false
```

---

## 🌍 Мультиязычность

Все сообщения вынесены в `lang/ru.yml` и `lang/en.yml`. Чтобы добавить новый язык:

1. Создайте `lang/<код>.yml` на основе `en.yml`.
2. Укажите `language: <код>` в `config.yml`.
3. Выполните `/playerstats reload`.

Формат сообщений — **MiniMessage** (`<red>`, `<gold>`, `<gradient:...>`).

---

## 🔌 PlaceholderAPI

Плейсхолдеры регистрируются автоматически с **кэшем** (обновление раз в 5 секунд).

| Плейсхолдер | Описание |
| :--- | :--- |
| `%playerstats_play_time%` | Время игры (форматированное) |
| `%playerstats_play_time_raw%` | Время игры (в секундах) |
| `%playerstats_kills%` | Убийства |
| `%playerstats_deaths%` | Смерти |
| `%playerstats_kdr%` | K/D |
| `%playerstats_balance%` | Баланс |
| `%playerstats_group%` | Группа |
| `%playerstats_blocks_placed%` | Поставлено блоков |
| `%playerstats_blocks_broken%` | Сломано блоков |
| `%playerstats_mobs_killed%` | Убито мобов |
| `%playerstats_bosses_killed%` | Убито боссов |

После установки:

```
/papi reload
```

---

## 🎁 Milestones (награды за достижения)

Настраиваются в `config.yml`. При достижении порога игрок получает сообщение и команды от консоли.

```yaml
milestones:
  enabled: true
  mobs_killed:
    100:
      message: "<gold>🏆 Вы убили 100 мобов!"
      commands:
        - "give %player% diamond 1"
```

**Плейсхолдеры:** `%player%`, `%value%`.

---

## 💬 Discord Webhook

Уведомления о достижениях в Discord. Настраивается в `config.yml`:

```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."
  username: "PlayerStats"
  events:
    milestone: true
```

---

## 🛠 Сборка

```bash
git clone https://github.com/luci4ka/PlayerStats.git
cd PlayerStats
./gradlew shadowJar
```

Готовый JAR: `build/libs/PlayerStats-1.0.jar`.

### Требования для сборки

* **JDK 21+** (для Paper 1.21.11)
* **Gradle 9.1.0+** (включён в wrapper)

---

## 📁 Структура

```
src/main/java/com/playerstats/
├── api/                    # HTTP API
├── core/
│   ├── commands/           # Команды
│   ├── config/             # ConfigHandler, LanguageManager
│   ├── database/           # MySQL, SQLite, DAO
│   ├── gui/                # StatsGui
│   ├── listeners/          # Слушатели событий
│   ├── multithreading/     # ThreadManager
│   └── services/           # PlayerGroupService, TopCache, UpdateChecker, MilestoneManager, DiscordWebhook
├── hooks/                  # Хуки плагинов
└── Main.java

src/main/resources/
├── config.yml
├── paper-plugin.yml
└── lang/
    ├── ru.yml
    └── en.yml
```

---

## 🐛 Известные проблемы

| Проблема | Решение |
| :--- | :--- |
| SQLite медленный при больших данных | Используйте MySQL |
| API без защиты | Ограничьте доступ через firewall |
| Milestones сбрасываются при рестарте | Храните флаги в БД (планируется) |

---

## 📄 Лицензия

MIT

---