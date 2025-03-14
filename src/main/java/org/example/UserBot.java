package org.example;

import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.APIToken;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.GetChat;
import it.tdlight.jni.TdApi.MessageContent;
import it.tdlight.jni.TdApi.MessageText;
import it.tdlight.util.UnsupportedNativeLibraryException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Класс юзербота на базе TDLight Java.
 * Для создания экземпляра необходимо передать apiId, apiHash и поставщика аутентификации.
 * Также можно зарегистрировать обработчики входящих сообщений.
 */
public class UserBot implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(UserBot.class.getName());
    private final SimpleTelegramClient client;
    private final SimpleTelegramClientFactory clientFactory;
    private final List<Consumer<TdApi.UpdateNewMessage>> messageHandlers = new ArrayList<>();

    /**
     * Конструктор юзербота.
     *
     * @param apiId                   ваш api_id
     * @param apiHash                 ваш api_hash
     * @param phone  поставщик аутентификации (например, AuthenticationSupplier.consoleLogin())
     * @throws IOException            при ошибке настройки логирования
     */
    public UserBot(int apiId, String apiHash, String phone) throws IOException, UnsupportedNativeLibraryException {
        // Инициализация нативных библиотек и логирования TDLight
        Init.init();
        Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
        setupFileLogger();

        AuthenticationSupplier<?> authenticationSupplier = AuthenticationSupplier.user(phone);


        // Создаём фабрику клиентов
        this.clientFactory = new SimpleTelegramClientFactory();

        // Формируем APIToken из переданных apiId и apiHash
        APIToken apiToken = new APIToken(apiId, apiHash);

        // Настраиваем TDLibSettings с указанием путей для сессии и загрузок
        TDLibSettings settings = TDLibSettings.create(apiToken);
        Path sessionPath = Paths.get("userbot-session-" + phone);
        settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
        settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

        // Получаем билдера клиента
        SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);

        // Регистрируем обработчик новых сообщений, который пересылает обновление всем зарегистрированным обработчикам
        clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, update -> {
            for (Consumer<TdApi.UpdateNewMessage> handler : messageHandlers) {
                handler.accept(update);
            }
        });

        // Создаём клиента, передавая данные аутентификации
        this.client = clientBuilder.build(authenticationSupplier);
    }

    /**
     * Настраивает логирование в файл "userbot.log" через java.util.logging.
     */
    private void setupFileLogger() throws IOException {
        FileHandler fileHandler = new FileHandler("userbot.log", true);
        fileHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(fileHandler);
    }

    /**
     * Регистрирует обработчик входящих сообщений.
     *
     * @param handler обработчик, принимающий обновление типа UpdateNewMessage
     */
    public void addMessageHandler(Consumer<TdApi.UpdateNewMessage> handler) {
        messageHandlers.add(handler);
    }

    /**
     * Пример метода отправки текстового сообщения в указанный чат.
     *
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    public void sendMessage(long chatId, String text) {
        TdApi.SendMessage req = new TdApi.SendMessage();
        req.chatId = chatId;
        TdApi.InputMessageText inputMessageText = new TdApi.InputMessageText();
        inputMessageText.text = new TdApi.FormattedText(text, new TdApi.TextEntity[0]);
        req.inputMessageContent = inputMessageText;
        client.sendMessage(req, true).whenComplete((message, error) -> {
            if (error != null) {
                logger.warning("Ошибка отправки сообщения: " + error);
            } else {
                logger.info("Сообщение отправлено: " + message);
            }
        });
    }

    /**
     * Возвращает внутренний клиент, если требуется доп. настройка.
     */
    public SimpleTelegramClient getClient() {
        return client;
    }

    /**
     * Закрывает клиента и освобождает ресурсы.
     */
    @Override
    public void close() throws Exception {
        client.close();
        clientFactory.close();
    }
}
