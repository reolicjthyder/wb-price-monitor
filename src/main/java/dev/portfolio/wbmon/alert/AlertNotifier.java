package dev.portfolio.wbmon.alert;

/** Транспорт для отправки алертов владельцу. Реализуется Telegram-обработчиком в Task 6. */
public interface AlertNotifier {

    void send(long chatId, String text);
}
