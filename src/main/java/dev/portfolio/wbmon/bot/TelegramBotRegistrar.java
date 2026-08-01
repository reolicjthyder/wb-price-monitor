package dev.portfolio.wbmon.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Поднимает long polling после старта контекста. Без токена приложение стартует молча
 * (нужно для тестов и локальной сборки), а ошибка регистрации не роняет поллер цен.
 */
@Component
public class TelegramBotRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRegistrar.class);

    private final TelegramBotHandler handler;
    private final String botToken;

    public TelegramBotRegistrar(TelegramBotHandler handler,
                                @Value("${wbmon.telegram.bot-token}") String botToken) {
        this.handler = handler;
        this.botToken = botToken;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (botToken.isBlank()) {
            log.warn("TELEGRAM_BOT_TOKEN is empty — long polling disabled, alerts will only be logged");
            return;
        }
        try {
            new TelegramBotsApi(DefaultBotSession.class).registerBot(handler);
            log.info("Telegram long polling started for @{}", handler.getBotUsername());
        } catch (Exception e) {
            log.error("Failed to register Telegram bot — price polling continues without alerts", e);
        }
    }
}
