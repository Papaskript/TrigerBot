package org.example;

import it.tdlight.util.UnsupportedNativeLibraryException;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Бот, реализующий базовую логику обработки входящих сообщений.
 * Метод onUpdateReceived делегирует обработку команды /add в Main.
 */
public class Bot extends TelegramLongPollingBot {
    private final BotConfig config;
    private boolean waitingForCode = false;
    private ProgramFlow flow;
    private CompletableFuture<String> codeFuture = new CompletableFuture<>();

    public Bot(BotConfig config, ProgramFlow flow) {
        this.config = config;
        this.flow = flow;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().getChatId() == config.getAdminId()) {
            Message message = update.getMessage();


            if (message.getReplyToMessage() != null) {
                this.flow.handleReply(message);
            } else {
                String text = message.getText();
                if (this.waitingForCode) {
                    this.codeFuture.complete(text);
                    this.codeFuture = new CompletableFuture<>();
                    this.waitingForCode = false;
                }

                // Если команда /add, делегируем обработку в ProgramFlow
                else if (text.startsWith("/add")) {
                    try {
                        this.flow.processAddCommand(message);
                    } catch (UnsupportedNativeLibraryException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }

            }
        }
    }

    public CompletableFuture<String> waitForCode() {
        this.waitingForCode = true;
        return this.codeFuture;
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return config.getBotToken();
    }

    /**
     * Метод для отправки сообщений с опциональными параметрами
     * @param text   объект с данными сообщения
     */
    public Message send_message(String text) {
        // Отправка текстового сообщения
        if (text != null && !text.isEmpty()) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(String.valueOf(this.config.getAdminId()))
                    .text(text)
                    .build();
            try {
                return execute(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public Message execute(PartialBotApiMethod<?> partialBotApiMethod) throws TelegramApiException {
        if (partialBotApiMethod instanceof SendMessage) {
            return this.execute((SendMessage) partialBotApiMethod);
        } else if (partialBotApiMethod instanceof SendPhoto) {
            return this.execute((SendPhoto) partialBotApiMethod);
        } else if (partialBotApiMethod instanceof SendVoice) {
            return this.execute((SendVoice) partialBotApiMethod);
        } else if (partialBotApiMethod instanceof SendSticker) {
            return this.execute((SendSticker) partialBotApiMethod);
        } else if (partialBotApiMethod instanceof SendAnimation) {
            return this.execute((SendAnimation) partialBotApiMethod);
        }
        return null;
    }

    /**
     * Класс для передачи опциональных данных сообщения.
     */
    public static class MessageData {
        public String text;      // Текст сообщения
        public String photoUrl;  // URL изображения (если требуется)
        public String voiceUrl;  // URL голосового сообщения (если требуется)
    }
}
