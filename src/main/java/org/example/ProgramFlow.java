package org.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.tdlight.jni.TdApi;
import it.tdlight.util.UnsupportedNativeLibraryException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProgramFlow {
    private Bot bot;
    private UserBot userBot;
    private MessageMapper messageMapper;
    private ReverseMessageConverter reverseConverter;

    public ProgramFlow() {
        this.messageMapper = new MessageMapper();
    }

    public void registerBot() throws TelegramApiException, IOException {
        // Загружаем настройки из файла config.json
        BotConfig config = BotConfig.load(Path.of("config.json"));
        // Создаем и регистрируем бота
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        this.bot = new Bot(config, this);
        this.reverseConverter = new ReverseMessageConverter(this.bot.getBotToken());
        botsApi.registerBot(this.bot);
        System.out.println("Bot started successfully.");
    }

    public void registerUserBot() throws UnsupportedNativeLibraryException, IOException {
        // Читаем JSON-файл credentials.json, который должен находиться в корне проекта
        String json = new String(Files.readAllBytes(Paths.get("credentials.json")));
        Gson gson = new Gson();
        Type listType = new TypeToken<List<Credential>>() {}.getType();
        List<Credential> credentials = gson.fromJson(json, listType);

        if (credentials.isEmpty()) {
            Main.logger.severe("Файл credentials.json не содержит ни одной записи.");
            return;
        }
        // Выбираем первую запись из списка
        Credential cred = credentials.get(0);
        int apiId = cred.getApi_id();
        String apiHash = cred.getApi_hash();
        String phoneNumber = cred.getPhonenumber();

        // Создаем экземпляр UserBot с использованием полученных параметров
        try {
            this.userBot = new UserBot(apiId, apiHash, phoneNumber);

            // Регистрируем обработчик входящих сообщений
            this.userBot.addMessageHandler(update -> {
                TdApi.MessageContent content = update.message.content;
                String text;
                if (content instanceof TdApi.MessageText messageText) {
                    text = messageText.text.text;
                } else {
                    text = "(" + content.getClass().getSimpleName() + ")";
                }
                long chatId = update.message.chatId;
                int messageDate = update.message.date;

                // Получаем информацию о чате
                this.userBot.getClient().send(new TdApi.GetChat(chatId))
                    .thenComposeAsync(chat -> {
                        // Проверяем тип чата и игнорируем групповые чаты
                        if (chat.type instanceof TdApi.ChatTypeBasicGroup || chat.type instanceof TdApi.ChatTypeSupergroup) {
                            return CompletableFuture.completedFuture(null);
                        }

                        // Получаем информацию об отправителе, если это пользователь
                        if (update.message.senderId instanceof TdApi.MessageSenderUser senderUser) {
                            long userId = senderUser.userId;
                            if (senderUser.userId != this.userBot.getClient().getMe().id) {
                                return userBot.getClient().send(new TdApi.GetUser(userId))
                                        .thenApply(user -> new Object[]{chat, user});
                            } else {
                                return CompletableFuture.completedFuture(new Object[]{chat, null});
                            }
                        } else {
                            return CompletableFuture.completedFuture(new Object[]{chat, null});
                        }
                    })
                    .whenCompleteAsync((result, error) -> {
                        if (error != null) {
                            Main.logger.warning("Ошибка при получении информации о чате или пользователе: " + error);
                        } else if (result != null && ((TdApi.User) result[1]).id != this.userBot.getClient().getMe().id) {
                            TdApi.Chat chat = (TdApi.Chat) result[0];
                            TdApi.User user = (TdApi.User) result[1];

                            // Форматируем время сообщения
                            String formattedTime = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .withZone(java.time.ZoneId.systemDefault())
                                .format(java.time.Instant.ofEpochSecond(messageDate));

                            // Формируем строку с информацией об отправителе
                            String senderInfo = "";
                            if (user != null) {
                                if (user.usernames.activeUsernames.length != 0) {
                                    senderInfo += " (@" + user.usernames.activeUsernames[0] + ")";
                                }
                            }

                            // Логируем информацию о сообщении
                            Bot.MessageData data = new Bot.MessageData();
                            data.text = String.format("Cообщение от %s %s в %s:",
                                chat.title, senderInfo, formattedTime);
                            this.messageMapper.putMapping(Long.valueOf(this.bot.send_message(data).getMessageId()), update.message.chatId);
                            try {
                                Message sentMessage = this.bot.execute(MessageConverter.convertTdlightMessage(userBot.getClient(), update.message));
                                this.messageMapper.putMapping(Long.valueOf(sentMessage.getMessageId()), update.message.chatId);
                            } catch (TelegramApiException e) {
                                throw new RuntimeException(e);
                            }
                            TdApi.ViewMessages viewMessages = new TdApi.ViewMessages();
                            viewMessages.chatId = chatId;
                            viewMessages.messageIds = new long[]{update.message.id};  // Можно передавать массив сообщений
                            viewMessages.forceRead = true;  // Принудительное чтение

                            this.userBot.getClient().send(viewMessages, res -> {});
                            }
                        });
                });


            Main.logger.info("UserBot запущен. Ожидание входящих сообщений...");
            // Приложение работает до принудительного завершения

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Обрабатывает команду /add, извлекая аргументы из сообщения и отправляя ответ через send_message.
     *
     * @param message входящее сообщение с командой /add
     */
    public void processAddCommand(Message message) {
        String arguments = message.getText().replaceFirst("/add", "").trim();
        String responseText = "Добавлено: " + arguments;

        Bot.MessageData data = new Bot.MessageData();
        data.text = responseText;
        // Дополнительно можно установить photoUrl или voiceUrl, если требуется

        this.bot.send_message(data);
    }

    public void handleReply(Message message) {
        long chatId = this.messageMapper.getMapping(Long.valueOf(message.getReplyToMessage().getMessageId()));
        TdApi.SendMessage tdSendMessage = reverseConverter.convertTelegramMessage(message);
        // Отправляем сообщение через TDLight клиент
        this.userBot.getClient().send(tdSendMessage, result -> {
            TdApi.Message sentMessage = result.get();
            System.out.println("Сообщение успешно отправлено. ID: " + sentMessage.id);
        });
    }

    public void applyAuthCode(int code) {

    }
}
