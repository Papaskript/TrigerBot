package org.example;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Класс для загрузки конфигурации бота из JSON-файла.
 */
public class BotConfig {
    private String botToken;
    private String botUsername;
    private long adminId;

    public String getBotToken() {
        return botToken;
    }

    public String getBotUsername() {
        return botUsername;
    }

    public long getAdminId() {
        return adminId;
    }

    /**
     * Загружает конфигурацию из указанного файла.
     *
     * @param configPath путь к файлу config.json
     * @return объект BotConfig с настройками
     * @throws IOException если чтение файла не удалось
     */
    public static BotConfig load(Path configPath) throws IOException {
        String json = Files.readString(configPath);
        return new Gson().fromJson(json, BotConfig.class);
    }
}
