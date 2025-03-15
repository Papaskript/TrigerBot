package org.example;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.*;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.games.Animation;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

import java.util.List;

/**
 * Класс ReverseMessageConverter преобразует сообщение, полученное через Telegram Bots API,
 * в объект TdApi.SendMessage для отправки через TDLight (TDLib).
 * Для мультимедийных сообщений файлы скачиваются во временную директорию.
 */
public class ReverseMessageConverter {

    private final String botToken;

    /**
     * Конструктор, принимающий токен бота для загрузки файлов.
     *
     * @param botToken токен бота Telegram
     */
    public ReverseMessageConverter(String botToken) {
        this.botToken = botToken;
    }

    /**
     * Преобразует сообщение Telegram Bots API в запрос для TDLight (TDLib).
     * В случае мультимедийных сообщений происходит загрузка файла.
     *
     * @param message сообщение из Telegram Bots API
     * @return объект TdApi.SendMessage, готовый для отправки через TDLight
     */
    public TdApi.SendMessage convertTelegramMessage(Message message, long chatId) {
        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = chatId;
        sendMessage.options = new TdApi.MessageSendOptions();

        // Текстовое сообщение
        if (message.getText() != null && !message.getText().isEmpty()) {
            InputMessageText inputMessageText = new InputMessageText();
            inputMessageText.text = new TdApi.FormattedText(message.getText(), null);
            sendMessage.inputMessageContent = inputMessageText;
        }
        // Фото
        else if (message.getPhoto() != null && !message.getPhoto().isEmpty()) {
            List<PhotoSize> photos = message.getPhoto();
            // Выбираем наибольший размер
            PhotoSize largestPhoto = photos.get(photos.size() - 1);
            String fileId = largestPhoto.getFileId();
            try {
                // Загружаем файл по file_id
                String localPath = TelegramFileDownloader.downloadFile(fileId, botToken);
                InputMessagePhoto inputMessagePhoto = new InputMessagePhoto();
                inputMessagePhoto.photo = new TdApi.InputFileLocal(localPath);
                String caption = (message.getCaption() != null) ? message.getCaption() : "";
                inputMessagePhoto.caption = new TdApi.FormattedText(caption, null);
                sendMessage.inputMessageContent = inputMessagePhoto;
            } catch (Exception e) {
                // В случае ошибки формируем текстовое сообщение с информацией об ошибке
                InputMessageText inputMessageText = new InputMessageText();
                inputMessageText.text = new TdApi.FormattedText("Ошибка загрузки фото: " + e.getMessage(), null);
                sendMessage.inputMessageContent = inputMessageText;
            }
        }
        // Голосовое сообщение
        else if (message.getVoice() != null) {
            Voice voice = message.getVoice();
            String fileId = voice.getFileId();
            try {
                String localPath = TelegramFileDownloader.downloadFile(fileId, botToken);
                InputMessageVoiceNote inputMessageVoiceNote = new InputMessageVoiceNote();
                inputMessageVoiceNote.voiceNote = new TdApi.InputFileLocal(localPath);
                inputMessageVoiceNote.duration = voice.getDuration();
                sendMessage.inputMessageContent = inputMessageVoiceNote;
            } catch (Exception e) {
                InputMessageText inputMessageText = new InputMessageText();
                inputMessageText.text = new TdApi.FormattedText("Ошибка загрузки голосового сообщения: " + e.getMessage(), null);
                sendMessage.inputMessageContent = inputMessageText;
            }
        }
        // Стикер
        else if (message.getSticker() != null) {
            Sticker sticker = message.getSticker();
            String fileId = sticker.getFileId();
            try {
                String localPath = TelegramFileDownloader.downloadFile(fileId, botToken);
                InputMessageSticker inputMessageSticker = new InputMessageSticker();
                inputMessageSticker.sticker = new TdApi.InputFileLocal(localPath);
                sendMessage.inputMessageContent = inputMessageSticker;
            } catch (Exception e) {
                InputMessageText inputMessageText = new InputMessageText();
                inputMessageText.text = new TdApi.FormattedText("Ошибка загрузки стикера: " + e.getMessage(), null);
                sendMessage.inputMessageContent = inputMessageText;
            }
        }
        // Анимация (например, GIF)
        else if (message.getAnimation() != null) {
            Animation animation = message.getAnimation();
            String fileId = animation.getFileId();
            try {
                String localPath = TelegramFileDownloader.downloadFile(fileId, botToken);
                InputMessageAnimation inputMessageAnimation = new InputMessageAnimation();
                inputMessageAnimation.animation = new TdApi.InputFileLocal(localPath);
                String caption = (message.getCaption() != null) ? message.getCaption() : "";
                inputMessageAnimation.caption = new TdApi.FormattedText(caption, null);
                sendMessage.inputMessageContent = inputMessageAnimation;
            } catch (Exception e) {
                InputMessageText inputMessageText = new InputMessageText();
                inputMessageText.text = new TdApi.FormattedText("Ошибка загрузки анимации: " + e.getMessage(), null);
                sendMessage.inputMessageContent = inputMessageText;
            }
        }
        // Если тип сообщения не поддерживается
        else {
            InputMessageText inputMessageText = new InputMessageText();
            inputMessageText.text = new TdApi.FormattedText("Неподдерживаемый тип сообщения", null);
            sendMessage.inputMessageContent = inputMessageText;
        }

        return sendMessage;
    }
}

