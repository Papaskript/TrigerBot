package org.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CredentialsService {
    private static final String FILE_PATH = "credentials.json";
    private final Gson gson;
    private final List<Credential> credentials;

    public CredentialsService() {
        this.gson = new Gson();
        this.credentials = loadCredentials();
    }

    /**
     * Загружает список credentials из файла JSON.
     * Если файл не найден или пуст, возвращается пустой список.
     */
    private List<Credential> loadCredentials() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<Credential>>() {}.getType();
            List<Credential> list = gson.fromJson(reader, listType);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Добавляет новый Credential в список и сохраняет изменения в файле.
     *
     * @param credential новый Credential
     */
    public synchronized void addCredential(Credential credential) {
        credentials.add(credential);
        saveCredentials();
    }

    /**
     * Сохраняет текущий список credentials в файл JSON.
     */
    private synchronized void saveCredentials() {
        try (Writer writer = new FileWriter(FILE_PATH)) {
            gson.toJson(credentials, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Возвращает список credentials.
     *
     * @return список Credential
     */
    public List<Credential> getCredentials() {
        return credentials;
    }
}
