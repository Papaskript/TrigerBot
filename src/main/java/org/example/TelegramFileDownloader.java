package org.example;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Класс для загрузки файла из Telegram по file_id и сохранения его во временную директорию.
 */
public class TelegramFileDownloader {
    private static final Gson gson = new Gson();

    /**
     * Загружает файл по file_id и возвращает путь к временному файлу.
     *
     * @param fileId   идентификатор файла из Telegram Bots API
     * @param botToken токен бота для доступа к Telegram API
     * @return абсолютный путь к временному файлу
     * @throws Exception при ошибке получения информации или скачивания файла
     */
    public static String downloadFile(String fileId, String botToken) throws Exception {
        // Получаем информацию о файле через getFile метод Telegram Bot API
        String getFileUrl = "https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId;
        URL url = new URL(getFileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (InputStream responseStream = connection.getInputStream();
             InputStreamReader reader = new InputStreamReader(responseStream)) {
            TelegramFileResponse response = gson.fromJson(reader, TelegramFileResponse.class);
            if (!response.ok || response.result == null || response.result.file_path == null) {
                throw new Exception("Ошибка получения информации о файле от Telegram");
            }

            // Формируем URL для загрузки файла
            String fileDownloadUrl = "https://api.telegram.org/file/bot" + botToken + "/" + response.result.file_path;
            return downloadFileFromUrl(fileDownloadUrl, fileId);
        }
    }

    /**
     * Скачивает файл по заданному URL и сохраняет его во временный файл.
     *
     * @param fileDownloadUrl URL для загрузки файла
     * @param fileId          идентификатор файла (для формирования имени)
     * @return абсолютный путь к временному файлу
     * @throws Exception при ошибке загрузки файла
     */
    private static String downloadFileFromUrl(String fileDownloadUrl, String fileId) throws Exception {
        URL downloadUrl = new URL(fileDownloadUrl);
        HttpURLConnection downloadConnection = (HttpURLConnection) downloadUrl.openConnection();
        downloadConnection.setRequestMethod("GET");

        // Создаем временный файл
        File tempFile = File.createTempFile("telegram_", "_" + fileId);
        try (InputStream downloadStream = downloadConnection.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = downloadStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return tempFile.getAbsolutePath();
    }

    // Вспомогательные классы для разбора JSON ответа getFile
    private static class TelegramFileResponse {
        boolean ok;
        FileResult result;
    }

    private static class FileResult {
        String file_id;
        int file_size;
        String file_path;
    }
}

