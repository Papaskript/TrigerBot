package org.example;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.*;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MessageConverter {

    /**
     * Преобразует сообщение TDLight в формат для Telegram Bots API.
     * Для мультимедийных сообщений скачивает файл во временную директорию.
     *
     * @param client    клиент TDLight, необходим для загрузки файлов
     * @param tdMessage сообщение TDLight
     * @return объект BotApiMethod (например, SendMessage, SendPhoto, SendVoice и т.д.)
     */
    public static PartialBotApiMethod<?> convertTdlightMessage(SimpleTelegramClient client, Message tdMessage) {
        String chatId = String.valueOf(tdMessage.chatId);
        MessageContent content = tdMessage.content;

        // Текстовое сообщение
        if (content instanceof MessageText) {
            MessageText textMessage = (MessageText) content;
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(textMessage.text.text);
            return sendMessage;
        }
        // Фотография
        else if (content instanceof MessagePhoto) {
            MessagePhoto photoMessage = (MessagePhoto) content;
            Photo photo = photoMessage.photo;
            // Выбираем самый большой размер
            PhotoSize largestSize = photo.sizes[photo.sizes.length - 1];
            String filePath = downloadFileSync(client, largestSize.photo.id);
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId);
            sendPhoto.setPhoto(new InputFile(new File(filePath)));
            return sendPhoto;
        }
        // Голосовое сообщение
        else if (content instanceof MessageVoiceNote) {
            MessageVoiceNote voiceMessage = (MessageVoiceNote) content;
            VoiceNote voiceNote = voiceMessage.voiceNote;
            String filePath = downloadFileSync(client, voiceNote.voice.id);
            SendVoice sendVoice = new SendVoice();
            sendVoice.setChatId(chatId);
            sendVoice.setVoice(new InputFile(new File(filePath)));
            return sendVoice;
        }
        // Стикер
        else if (content instanceof MessageSticker) {
            MessageSticker stickerMessage = (MessageSticker) content;
            Sticker sticker = stickerMessage.sticker;
            String filePath = downloadFileSync(client, sticker.sticker.id);
            SendSticker sendSticker = new SendSticker();
            sendSticker.setChatId(chatId);
            sendSticker.setSticker(new InputFile(new File(filePath)));
            return sendSticker;
        }
        // Анимация (например, GIF)
        else if (content instanceof MessageAnimation) {
            MessageAnimation animationMessage = (MessageAnimation) content;
            Animation animation = animationMessage.animation;
            String filePath = downloadFileSync(client, animation.animation.id);
            SendAnimation sendAnimation = new SendAnimation();
            sendAnimation.setChatId(chatId);
            sendAnimation.setAnimation(new InputFile(new File(filePath)));
            return sendAnimation;
        }
        // Если тип не поддерживается, возвращаем текстовое уведомление
        else {
            SendMessage fallback = new SendMessage();
            fallback.setChatId(chatId);
            fallback.setText("Неподдерживаемый тип сообщения.");
            return fallback;
        }
    }

    /**
     * Скачивает файл с помощью TDLight и копирует его во временный файл.
     *
     * @param client TDLight клиент
     * @param fileId идентификатор файла из TDLight
     * @return путь к временному файлу
     */
    private static String downloadFileSync(SimpleTelegramClient client, int fileId) {
        CompletableFuture<TdApi.File> future = new CompletableFuture<>();
        client.send(new TdApi.DownloadFile(fileId, 1, 0, 0, true), result -> {
            future.complete(result.get());
        });

        TdApi.File file;
        try {
            // Ждём завершения скачивания (максимум 30 секунд)
            file = future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при скачивании файла", e);
        }

        // Копируем файл из пути, который вернул TDLight, во временную директорию
        try {
            File original = new File(file.local.path);
            // Создаём временный файл с префиксом "tdlight_"
            File tempFile = File.createTempFile("tdlight_", "_" + fileId);
            Files.copy(original.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка копирования файла во временную директорию", e);
        }
    }
}
