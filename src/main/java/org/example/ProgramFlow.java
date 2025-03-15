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
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ProgramFlow {
    private Bot bot;
    private Map<Long, UserBot> userBots;
    private MessageMapper messageMapper;
    private ReverseMessageConverter reverseConverter;
    private CredentialsService credentialsService;

    public ProgramFlow() throws TelegramApiException, IOException, UnsupportedNativeLibraryException {
        this.messageMapper = new MessageMapper();
        this.userBots = new HashMap<>();

        this.credentialsService = new CredentialsService();
        this.registerBot();
        for (Credential cred: this.credentialsService.getCredentials()) {
                int apiId = cred.getApi_id();
                String apiHash = cred.getApi_hash();
                String phoneNumber = cred.getPhonenumber();
                this.registerUserBot(apiId, apiHash, phoneNumber);
            }
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

    public void registerUserBot(int apiId, String apiHash, String phoneNumber) throws UnsupportedNativeLibraryException, IOException {


        // Создаем экземпляр UserBot с использованием полученных параметров
        try {
            this.userBots.put((long) apiId, new UserBot(apiId, apiHash, phoneNumber, this.bot));

            // Регистрируем обработчик входящих сообщений
            this.userBots.get((long) apiId).addMessageHandler(update -> {
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
                this.userBots.get((long) apiId).getClient().send(new TdApi.GetChat(chatId))
                    .thenComposeAsync(chat -> {
                        // Проверяем тип чата и игнорируем групповые чаты
                        if (chat.type instanceof TdApi.ChatTypeBasicGroup || chat.type instanceof TdApi.ChatTypeSupergroup) {
                            return CompletableFuture.completedFuture(null);
                        }

                        // Получаем информацию об отправителе, если это пользователь
                        if (update.message.senderId instanceof TdApi.MessageSenderUser senderUser) {
                            long userId = senderUser.userId;
                            if (senderUser.userId != this.userBots.get((long) apiId).getClient().getMe().id) {
                                return this.userBots.get((long) apiId).getClient().send(new TdApi.GetUser(userId))
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
                        } else if (result != null && ((TdApi.User) result[1]).id != this.userBots.get((long) apiId).getClient().getMe().id) {
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


                            String caption = String.format("New message from %s %s:\n",
                                chat.title, senderInfo, formattedTime);
                            try {
                                Message sentMessage = this.bot.execute(MessageConverter.convertTdlightMessage(this.userBots.get((long) apiId).getClient(), update.message, caption));
                                this.messageMapper.putMapping(Long.valueOf(sentMessage.getMessageId()), (long) apiId, update.message.chatId);
                            } catch (TelegramApiException e) {
                                throw new RuntimeException(e);
                            }
                            TdApi.ViewMessages viewMessages = new TdApi.ViewMessages();
                            viewMessages.chatId = chatId;
                            viewMessages.messageIds = new long[]{update.message.id};  // Можно передавать массив сообщений
                            viewMessages.forceRead = true;  // Принудительное чтение

                            this.userBots.get((long) apiId).getClient().send(viewMessages, res -> {});
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
    public void processAddCommand(Message message) throws UnsupportedNativeLibraryException, IOException {
        String[] arguments = message.getText().replaceFirst("/add", "").trim().split("\\s+");

        Credential newUserCredential = new Credential(Integer.parseInt(arguments[0]), arguments[1], arguments[2]);
        this.credentialsService.addCredential(newUserCredential);
        this.registerUserBot(newUserCredential.getApi_id(), newUserCredential.getApi_hash(), newUserCredential.getPhonenumber());
        this.bot.send_message("Добавление пользователя...");
    }

    public void handleReply(Message message) {
        AbstractMap.SimpleEntry<Long, Long> replyMap = this.messageMapper.getMapping(Long.valueOf(message.getReplyToMessage().getMessageId()));
        long chatId = replyMap.getValue();
        long apiId = replyMap.getKey();
        TdApi.SendMessage tdSendMessage = reverseConverter.convertTelegramMessage(message, chatId);
        // Отправляем сообщение через TDLight клиент
        this.userBots.get((long) apiId).getClient().send(tdSendMessage, result -> {
            TdApi.Message sentMessage = result.get();
            System.out.println("Сообщение успешно отправлено. ID: " + sentMessage.id);
        });
    }
}
