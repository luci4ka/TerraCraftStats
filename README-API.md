
# PlayerStats — API

Публичный Java API плагина **PlayerStats**. Позволяет другим плагинам читать
статистику игроков и топы, не завися от внутренних классов.

> **Важно:** подключение — только `compileOnly` / `provided`. **Не shade'ите**
> API в свой JAR — иначе `instanceof PlayerStatChangeEvent` и `getHandlerList()`
> не будут работать (два разных classloader'а загрузят два класса с одним именем).

---

## 1. Зависимость

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.luci4ka:PlayerStats:1.0.1' // ← подставь актуальную версию
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.luci4ka</groupId>
        <artifactId>PlayerStats</artifactId>
        <version>1.0.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

---

## 2. `plugin.yml`

Если PlayerStats **обязателен**:

```yaml
depend: [PlayerStats]
```

Если опционален:

```yaml
softdepend: [PlayerStats]
```

`softdepend` гарантирует, что PlayerStats загрузится **до** твоего плагина, если установлен.

---

## 3. Быстрый старт

```java
import ru.terracraft.stats.api.PlayerStatsApi;
import ru.terracraft.stats.api.PlayerStatsApiProvider;

public class MyPlugin extends JavaPlugin {

    private PlayerStatsApi statsApi;

    @Override
    public void onEnable() {
        // Вариант A: через фасад (рекомендуется)
        this.statsApi = PlayerStatsApiProvider.get();

        // Вариант B: напрямую через ServicesManager
        // var provider = getServer().getServicesManager()
        //         .getRegistration(PlayerStatsApi.class);
        // this.statsApi = provider != null ? provider.getProvider() : null;

        if (statsApi == null) {
            getLogger().warning("PlayerStats не установлен — статистика недоступна.");
        } else {
            getLogger().info("PlayerStats API подключён.");
        }
    }

    public int getKills(Player player) {
        if (statsApi == null) return 0;
        return statsApi.getStat(player, "kills");
    }
}
```

`PlayerStatsApiProvider` также умеет:

```java
PlayerStatsApiProvider.isAvailable();   // true, если API зарегистрировано
PlayerStatsApiProvider.getRequired();   // бросает IllegalStateException, если API нет
```

---

## 4. Методы API

### Статистика

```java
int kills = api.getStat(player, "kills");              // по Player (возвращает 0, если игрока нет)
int mobs  = api.getStat(uuid, "mobs_killed");          // по UUID

double balance = api.getStatDouble(uuid, "balance");   // double-стата (баланс)
String group   = api.getStatString(uuid, "group");     // string-стата (группа) или null
```

**Контракты:**

| Метод | Возврат при «нет данных» |
|---|---|
| `getStat(Player, String)` | `0` |
| `getStat(UUID, String)` | `0` |
| `getStatDouble(UUID, String)` | `0.0` |
| `getStatString(UUID, String)` | `null` |

> Все методы `getStat*` **ходят в БД** (SQL), даже если игрок не найден.
> `getStat(Player, ...)` — просто прокси на `getStat(UUID, ...)`.

### Снимок всех стат

```java
PlayerStatsSnapshot snap = api.getSnapshot(uuid);
if (snap != null) {
    int kills      = snap.intStats().getOrDefault("kills", 0);
    double balance = snap.doubleStats().getOrDefault("balance", 0.0);
    String group   = snap.stringStats().getOrDefault("group", "default");
}
```

**Контракт `getSnapshot`:**

- Возвращает `null`, если игрока нет в БД.
- `intStats` / `doubleStats` — **включают нули**, если они записаны (не «только ненулевые»).
- `stringStats` — только не-`null` строки.
- `name` может быть `null`, если имя не найдено.
- Все карты **иммутабельны** (`Map.copyOf`).

**Тип статы определяется по имени** (для `balance` — `doubleStats`, для остальных — `intStats`). Если добавляешь новую double-стату — обнови `DOUBLE_STATS` в `PlayerStatsApiImpl`.

### Топы

```java
// Топ-10 по убийствам
List<LeaderboardEntry> top = api.getTop("kills", 10);
for (LeaderboardEntry e : top) {
    System.out.printf("#%d %s — %.0f%n", e.rank(), e.playerName(), e.value());
}

// Место игрока в топе (0 = не в топе)
int rank = api.getRank(uuid, "kills");
```

**Контракты:**

| Метод | Возврат при «нет данных» |
|---|---|
| `getTop(String, int)` | пустой список (`List.of()`) при `limit <= 0` или ошибке БД |
| `getRank(UUID, String)` | `0` (не «-1»), если игрок не найден / нет записи |

**Источники `getTop`:**

1. **`TopCache`** (in-memory) — если стата кэшируется. **Никогда не блокирует main-поток.**
2. **`LeaderboardDAO`** (SQL) — fallback для не-кэшируемых стат.

`LeaderboardEntry` — иммутабельный `record`: `rank` (1-based), `uuid`, `playerName`, `value`.

### Игроки

```java
UUID uuid = api.getUuidByName("Notch");         // null если не найден
String name = api.getNameByUuid(uuid);          // null если не найден
int playerId = api.getPlayerId(uuid);           // -1 если не найден
```

**Контракты:**

| Метод | Возврат при «нет данных» |
|---|---|
| `getUuidByName(String)` | `null` |
| `getNameByUuid(UUID)` | `null` |
| `getPlayerId(UUID)` | `-1` |

### Утилиты

```java
boolean excluded = api.isExcluded(uuid);        // исключён из статистики?
int serverId = api.getServerId();               // ID текущего сервера
String serverName = api.getServerName();        // имя текущего сервера
```

`isExcluded` — безопасен (`ConcurrentHashMap`), если `ExcludeCommand` не инициализирован — вернёт `false`.

`getServerId` / `getServerName` — **в памяти**, безопасно.

---

## 5. События

Все события — в `ru.terracraft.stats.api.event.*`.

| Событие | Когда |
|---|---|
| `PlayerStatChangeEvent` | при изменении любой статы игрока (можно отменить или изменить `newValue`) |

> **Только одно событие.** Событий `PlayerAchievementUnlockEvent` /
> `PlayerAchievementResetEvent` / `PlayerMilestoneEvent` **нет** — achievements
> и milestones не входят в текущую сборку.

### `PlayerStatChangeEvent`

Вызывается **синхронно** (на main-потоке). Имеет `Cancellable`.

**Геттеры:**

| Метод | Описание |
|---|---|
| `getPlayerUuid()` | UUID игрока |
| `getStat()` | Имя статы (например, `"kills"`) |
| `getOldValue()` | Старое значение (до изменения) |
| `getNewValue()` | Новое значение (можно изменить через `setNewValue`) |
| `isCancelled()` | Отменено? |
| `setCancelled(boolean)` | Отменить |

**Пример:**

```java
public class KillsListener implements Listener {

    @EventHandler
    public void onStatChange(PlayerStatChangeEvent event) {
        if (!event.getStat().equals("kills")) return;
        if (event.isCancelled()) return;

        // Можно изменить:
        // event.setNewValue(event.getNewValue() + 5);

        // Или отменить:
        // event.setCancelled(true);
    }
}
```

> **Не забывай:** `@EventHandler` работает, только если у тебя в `plugin.yml`
> есть `depend: [PlayerStats]` (или `softdepend`) — иначе событие прилетит
> раньше, чем твой плагин загрузит класс события.

---

## 6. Что **нельзя** делать

### ❌ Не shade'ить API

```groovy
// ПЛОХО:
implementation 'com.github.luci4ka:PlayerStats:1.0.1'  // попадёт в твой JAR
```

Если ты shade'ишь API, в runtime будет **два класса** `PlayerStatChangeEvent`:

- один в твоём JAR (shaded),
- один в JAR PlayerStats.

Bukkit вызывает события **из своего classloader**, и `instanceof` не сработает.
`@EventHandler` не будет вызываться.

### ❌ Не кэшировать `PlayerStatsApi` между перезагрузками

Если PlayerStats перезагружается (`/reload` или `/playerstats reload`), API-инстанс
**может остаться тем же** (регистрация через `ServicesManager` не пересоздаётся),
но не полагайся на это. Если нужна свежесть — бери `PlayerStatsApiProvider.get()`
каждый раз.

### ❌ Не вызывать API из main-потока в цикле

`getStat`, `getTop` (fallback), `getRank`, `getSnapshot`, `getNameByUuid`,
`getPlayerId`, `getUuidByName` — **ходят в БД**, могут блокировать.
В цикле по 100 игрокам на main-потоке — это 100 SQL-запросов подряд, TPS просядет.

**Как правильно:**

- Из async-потока — можно.
- Из main — один-два вызова (например, по одному игроку) — приемлемо.
- Для часто вызываемых (PlaceholderAPI, GUI, scoreboard) — используй **свой кэш**
  или **готовый** `TopCache` (для топов) / `PlayerStatsExpansion` (для PAPI).

### ❌ Не вызывать `getSnapshot` каждые N тиков

`getSnapshot` делает **один большой SQL-запрос** + `getPlayerName` + сборку
`Map.copyOf` × 3. Для скорборда с обновлением 20 раз/сек — 20 запросов/сек.
Кэшируй результат сам (например, на 5–30 сек).

---

## 7. Thread-safety

API-классы **потокобезопасны** (DAO используют connection-per-call и `ConcurrentHashMap`-кэши).

**Можно вызывать из async-потоков.**

`getSnapshot` возвращает `PlayerStatsSnapshot` с **иммутабельными** `Map.copyOf(...)` —
безопасно передавать между потоками.

`getTop` использует `TopCache` (in-memory), fallback — SQL. При fallback — стандартная
потокобезопасность JDBC (connection-per-call).

`PlayerStatChangeEvent` — синхронное, `Cancellable`. Не вызывай из async-потока —
Bukkit сам вызовет на main через `StatsListener`.

---

## 8. Что **не входит** в текущую сборку

В этой версии плагина **отсутствуют**:

- HTTP API (`api.yml`, `ApiKeyManager`, `X-API-Key`, CORS, rate-limit).
- Achievements (`achievements.yml`, `AchievementManager`, GUI достижений).
- Milestones (`milestones.yml`, `MilestoneManager`).
- Holograms (`holograms.yml`, DecentHolograms).
- Автобэкапы БД (`backup.yml`, `DatabaseBackupTask`).

Если нужна какая-то из этих фич — открывай issue на GitHub.

---

## 9. PlaceholderAPI

Если нужны плейсхолдеры в других плагинах (scoreboard, tab, chat) — используй
**PlaceholderAPI**, а не Java API: `%ps_...%`. Полный список — в `README.md`.

**Кэш 30 сек + async fill** — SQL **никогда** не выполняется в `onRequest` PAPI.

---

## 10. Поддерживаемые версии

| PlayerStats | Paper | Java |
|---|---|------|
| 1.0.1 | 1.21+ | 25+  |

---

## 11. Обратная связь

Если нужен метод, которого нет — открывай issue на GitHub.