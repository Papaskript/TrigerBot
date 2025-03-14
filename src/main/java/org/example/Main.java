package org.example;

import java.io.IOException;
import java.util.logging.Logger;

import it.tdlight.util.UnsupportedNativeLibraryException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {

    public static final Logger logger = Logger.getLogger(Main.class.getName());


    public static void main(String[] args) {
        ProgramFlow flow = new ProgramFlow();
        try {
            flow.registerUserBot();

            flow.registerBot();
            Thread.currentThread().join();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnsupportedNativeLibraryException | InterruptedException | TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }


}
