package org.example;

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

/**
 * Бот, реализующий базовую логику обработки входящих сообщений.
 * Метод onUpdateReceived делегирует обработку команды /add в Main.
 */
public class Bot extends TelegramLongPollingBot {
    private final BotConfig config;
    private boolean waitingForCode = false;
    private ProgramFlow flow;

    public Bot(BotConfig config, ProgramFlow flow) {
        this.config = config;
        this.flow = flow;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();


            if (message.getReplyToMessage() != null) {
                this.flow.handleReply(message);
            } else {
                String text = message.getText();
                if (this.waitingForCode) {
                    try {
                        int code = Integer.parseInt(text);
                        this.flow.applyAuthCode(code);
                        this.waitingForCode = false;
                    } catch (NumberFormatException ignored) {

                    }
                }

                // Если команда /add, делегируем обработку в ProgramFlow
                else if (text.startsWith("/add")) {
                    this.flow.processAddCommand(message);
                    this.waitingForCode = true;
                    }

            }
        }
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
     * Метод для отправки сообщений с опциональными параметрами.
     * Если заданы несколько полей (text, photoUrl, voiceUrl), отправляются соответствующие сообщения.
     *
     * @param data   объект с данными сообщения
     */
    public Message send_message(MessageData data) {
        // Отправка текстового сообщения
        if (data.text != null && !data.text.isEmpty()) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(String.valueOf(this.config.getAdminId()))
                    .text(data.text)
                    .build();
            try {
                return execute(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
        // Отправка фото, если указан URL
        if (data.photoUrl != null && !data.photoUrl.isEmpty()) {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(String.valueOf(this.config.getAdminId()));
            sendPhoto.setPhoto(new InputFile(data.photoUrl));
            try {
                return execute(sendPhoto);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
        // Отправка голосового сообщения, если указан URL
        if (data.voiceUrl != null && !data.voiceUrl.isEmpty()) {
            SendVoice sendVoice = new SendVoice();
            sendVoice.setChatId(String.valueOf(this.config.getAdminId()));
            sendVoice.setVoice(new InputFile(data.voiceUrl));
            try {
                return execute(sendVoice);
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
