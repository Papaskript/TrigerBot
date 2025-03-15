package org.example;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.tdlight.util.UnsupportedNativeLibraryException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {

    public static final Logger logger = Logger.getLogger(Main.class.getName());


    public static void main(String[] args) {
        ProgramFlow flow = null;
        try {
            flow = new ProgramFlow();
            Thread.currentThread().join();
        } catch (TelegramApiException | UnsupportedNativeLibraryException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }


}
