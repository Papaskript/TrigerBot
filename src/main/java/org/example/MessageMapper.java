package org.example;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Класс MessageMapper хранит отображение между идентификаторами сообщений.
 * Данные загружаются из скрытого JSON-файла при создании экземпляра,
 * а изменения автоматически сохраняются при обновлении маппинга.
 */
public class MessageMapper {

    // JSON файл для хранения данных
    private static final String DEFAULT_FILE_PATH = "message_mapper.json";

    // Отображение: ключ – исходный message_id, значение – привязанный message_id
    private Map<Long, AbstractMap.SimpleEntry<Long, Long>> mapping;
    private final Gson gson = new Gson();
    private final String filePath;

    /**
     * Конструктор по умолчанию, использующий путь по умолчанию.
     */
    public MessageMapper() {
        this(DEFAULT_FILE_PATH);
    }

    /**
     * Конструктор, позволяющий задать путь к файлу.
     *
     * @param filePath путь к JSON файлу для хранения данных
     */
    public MessageMapper(String filePath) {
        this.filePath = filePath;
        loadFromFile();
    }

    /**
     * Получает привязанный идентификатор по исходному messageId.
     *
     * @param messageId исходный идентификатор сообщения
     * @return привязанный message_id или null, если привязка отсутствует
     */
    public synchronized AbstractMap.SimpleEntry<Long, Long> getMapping(Long messageId) {
        return mapping.get(messageId);
    }

    /**
     * Добавляет или обновляет привязку для заданного messageId.
     * После изменения данные автоматически сохраняются в файл.
     *
     * @param messageId      исходный идентификатор сообщения
     * @param chatId привязанный идентификатор сообщения
     */
    public synchronized void putMapping(Long messageId, Long userBotId, Long chatId) {
        AbstractMap.SimpleEntry<Long, Long> bound = new AbstractMap.SimpleEntry<>(userBotId, chatId);
        mapping.put(messageId, bound);
        saveToFile();
    }

    /**
     * Удаляет привязку для указанного messageId.
     * После изменения данные автоматически сохраняются в файл.
     *
     * @param messageId идентификатор сообщения, привязку которого требуется удалить
     */
    public synchronized void removeMapping(Long messageId) {
        mapping.remove(messageId);
        saveToFile();
    }

    /**
     * Сохраняет текущее отображение в JSON файл.
     * Метод вызывается автоматически при каждом изменении.
     */
    private synchronized void saveToFile() {
        try (FileWriter writer = new FileWriter(filePath)) {
            Map<String, List<Long>> jsonCompatibleMap = new HashMap<>();
            for (Map.Entry<Long, AbstractMap.SimpleEntry<Long, Long>> entry : mapping.entrySet()) {
                jsonCompatibleMap.put(
                    entry.getKey().toString(),
                    Arrays.asList(entry.getValue().getKey(), entry.getValue().getValue())
                );
            }
            gson.toJson(jsonCompatibleMap, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Загружает отображение из JSON файла.
     * Если файл не существует или содержит некорректные данные, создаётся пустой маппинг.
     */
    private synchronized void loadFromFile() {
        File file = new File(filePath);
        if (!file.exists()) {
            mapping = new HashMap<>();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<HashMap<Long, Long>>() {}.getType();
            mapping = deserializeMap(reader);
            if (mapping == null) {
                mapping = new HashMap<>();
            }
        } catch (IOException e) {
            e.printStackTrace();
            mapping = new HashMap<>();
        }
    }

    public static Map<Long, AbstractMap.SimpleEntry<Long, Long>> deserializeMap(FileReader json) {
        // Создаем Gson
        Gson gson = new Gson();

        // Определяем тип данных Map<String, List<Long>>
        Type type = new TypeToken<Map<String, List<Long>>>() {}.getType();
        Map<String, List<Long>> jsonMap = gson.fromJson(json, type);

        // Преобразуем обратно в Map<Long, SimpleEntry<Long, Long>>
        Map<Long, AbstractMap.SimpleEntry<Long, Long>> resultMap = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : jsonMap.entrySet()) {
            Long key = Long.parseLong(entry.getKey());
            List<Long> values = entry.getValue();
            if (values.size() == 2) {
                resultMap.put(key, new AbstractMap.SimpleEntry<>(values.get(0), values.get(1)));
            }
        }
        return resultMap;
    }
}

