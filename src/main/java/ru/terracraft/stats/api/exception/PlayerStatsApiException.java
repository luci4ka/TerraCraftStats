package ru.terracraft.stats.api.exception;

/**
 * Исключение, которое могут бросать методы API в случае критической ошибки.
 *
 * <p>Сейчас реализация {@code PlayerStatsApiImpl} везде возвращает дефолтные
 * значения и <b>не бросает</b> это исключение. Класс оставлен для будущего
 * использования (когда какие-то методы начнут бросать checked-ошибки) и
 * для того, чтобы потребитель мог ловить {@link RuntimeException} в общем виде.</p>
 */
public class PlayerStatsApiException extends RuntimeException {

    public PlayerStatsApiException(String message) {
        super(message);
    }

    public PlayerStatsApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public PlayerStatsApiException(Throwable cause) {
        super(cause);
    }
}