# 📊 PlayerStats — статистика игроков для Paper 1.21

**PlayerStats** — плагин для отслеживания и отображения статистики игроков с поддержкой MySQL/SQLite, HTTP API, мультисерверности и интеграциями.

[![Version](https://img.shields.io/github/v/release/luci4ka/PlayerStats)](https://github.com/luci4ka/PlayerStats/releases/tag/1.1.2)
[![Paper](https://img.shields.io/badge/Paper-1.21-blue.svg)](https://papermc.io)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21-green.svg)](https://papermc.io)
[![Web API](https://img.shields.io/badge/API-HTTP-orange.svg)](#-http-api)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)
---

## ✨ Возможности

### 📈 Статистика игроков

* ⏳ Время игры (в секундах)
* ⛏ Блоки (поставлено / сломано)
* ☠ PvE (мобы, боссы)
* ⚔ PvP (убийства / смерти)
* 💰 Баланс (Vault)
* 🎖 Группы (LuckPerms)

### 🌐 HTTP API

* Встроенный HTTP-сервер (JDK `com.sun.net.httpserver`)
* JSON-ответы
* Настраиваемый хост и порт

### 🏆 Топы

* Время, PvP, PvE, блоки, баланс
* Форматирование значений
* Кликабельные имена игроков

### 🔗 Интеграции (система хуков)

* **LuckPerms** — группы игроков
* **Vault** — экономика
* **EssentialsX** — AFK и GodMode
* **PlaceholderAPI** — плейсхолдеры
* **DecentHolograms** — голограммы
* **bStats** — метрики

### 🛡 Защита от накрутки

* Исключение игроков (`/exclude`)
* Игнорирование Creative / Spectator
* Игнорирование AFK (EssentialsX)
* Игнорирование GodMode

### 💾 База данных

* **MySQL** (HikariCP)
* **SQLite** (WAL-режим)
* Автоматическое создание таблиц

### 🌍 Мультисерверность

* Общая статистика игрока
* Статистика по серверу
* Топы по серверам

---

## 📋 Команды

| Команда                   | Описание                 | Право                   |                       |
| ------------------------- | ------------------------ | ----------------------- | --------------------- |
| `/stats`                  | Показать свою статистику | `playerstats.stats`     |                       |
| `/top`                    | Показать топы            | `playerstats.top`       |                       |
| `/share [игрок]`          | Поделиться статистикой   | `playerstats.share`     |                       |
| `/exclude <игрок> [remove list]` |      Управление исключениями             |  `playerstats.exclude` | |
| `/playerstats reload`     | Перезагрузка             | `playerstats.reload`    |                       |

---

## 🔌 HTTP API

Включается в `config.yml`:

```yaml
api:
  enabled: true
  host: "0.0.0.0" # доступ с любого IP
  port: 8080
```

> ⚠ API не имеет встроенной аутентификации.
> Рекомендуется ограничить доступ через firewall или reverse proxy (Nginx).

### Эндпоинты

| Метод | Путь                      | Описание           |
| ----- | ------------------------- | ------------------ |
| GET   | `/api/server/{serverId}`  | Статистика сервера |
| GET   | `/api/total`              | Общая статистика   |
| GET   | `/api/player/{playerId}`  | Статистика игрока  |
| GET   | `/api/top/{stat}/{limit}` | Топ игроков        |
| GET   | `/api/health`             | Проверка БД        |

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

**`/api/health`**

```json
{
  "status": "ok",
  "database": "connected"
}
```

---

## 📦 Установка

### Требования

* **Java 21**
* **Paper 1.21**

### Шаги

1. Скачайте `PlayerStats-1.0.jar` из [Releases](https://github.com/luci/PlayerStats/releases)
2. Поместите файл в `plugins/`
3. Перезапустите сервер
4. Настройте `plugins/PlayerStats/config.yml`
5. Выполните `/playerstats reload`

---

## ⚙️ Конфигурация (`config.yml`)

```yaml
config-version: 1
debug-level: 1

database:
  type: sqlite
  host: localhost
  port: 3306
  name: playerstats
  user: root
  password: "your_password"

sqlite:
  file: plugins/PlayerStats/playerstats.db

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
```

---

## 🔌 PlaceholderAPI

Плейсхолдеры регистрируются автоматически.

| Плейсхолдер               | Описание   |
| ------------------------- | ---------- |
| `%playerstats_play_time%` | Время игры |
| `%playerstats_kills%`     | Убийства   |
| `%playerstats_deaths%`    | Смерти     |
| `%playerstats_kdr%`       | K/D        |

После установки:

```
/papi reload
```

---

## 🛠 Сборка

```bash
git clone https://github.com/luci/PlayerStats.git
cd PlayerStats
./gradlew shadowJar
```

---

## 📁 Структура

```
src/main/java/com/playerStatsDev/
├── api/
├── hooks/
├── core/
```

---

## 🐛 Известные проблемы

| Проблема                            | Решение           |
| ----------------------------------- | ----------------- |
| SQLite медленный при больших данных | Используйте MySQL |
| API без защиты                      | Ограничьте доступ |

---

## 📄 Лицензия

MIT

---

## 👤 Автор

**luci** — https://github.com/luci4ka
