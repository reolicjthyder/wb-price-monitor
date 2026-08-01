package dev.portfolio.wbmon.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Временная реализация, чтобы приложение поднималось до появления Telegram-транспорта.
 * УДАЛЯЕТСЯ в Task 6 вместе с этим комментарием.
 */
@Component
public class LoggingAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);

    @Override
    public void send(long chatId, String text) {
        log.info("ALERT (no transport yet) chat={} text={}", chatId, text);
    }
}
