package org.example;

import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import it.tdlight.util.ScannerUtils;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class BotClientInteraction implements ClientInteraction {
    private final ExecutorService blockingExecutor;
    private final Authenticable authenticable;
    private final Bot bot;
    public static String qr = "";

    public BotClientInteraction(ExecutorService blockingExecutor, Authenticable authenticable,
                                Bot bot) {
        this.blockingExecutor = blockingExecutor;
        this.authenticable = authenticable;
        this.bot = bot;
    }

    @Override
    public CompletableFuture<String> onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo) {
        AuthenticationSupplier<?> authSupplier = this.authenticable.getAuthenticationSupplier();
        AuthenticationData authData = this.getAuthDataNowOrNull(authSupplier);
        return CompletableFuture.supplyAsync(() -> {
            boolean useRealWho = authData != null;
            String who;
            if (!useRealWho) {
                who = "login";
            } else if (authData.isQrCode()) {
                who = "QR login";
            } else if (authData.isBot()) {
                who = authData.getBotToken().split(":", 2)[0];
            } else {
                who = authData.getUserPhoneNumber();
            }

            boolean trim = false;
            String question;
            switch (parameter) {
                case ASK_FIRST_NAME:
                    question = "Enter first name";
                    trim = true;
                    break;
                case ASK_LAST_NAME:
                    question = "Enter last name";
                    trim = true;
                    break;
                case ASK_CODE:
                    question = "Enter authentication code";
                    ParameterInfoCode codeInfo = (ParameterInfoCode)parameterInfo;
                    question = question + "\n\tPhone number: " + codeInfo.getPhoneNumber();
                    question = question + "\n\tTimeout: " + codeInfo.getTimeout() + " seconds";
                    question = question + "\n\tCode type: " + codeInfo.getType().getClass().getSimpleName().replace("AuthenticationCodeType", "");
                    if (codeInfo.getNextType() != null) {
                        question = question + "\n\tNext code type: " + codeInfo.getNextType().getClass().getSimpleName().replace("AuthenticationCodeType", "");
                    }

                    trim = true;
                    break;
                case ASK_PASSWORD:
                    question = "Enter your password";
                    String passwordMessage = "Password authorization:";
                    String hint = ((ParameterInfoPasswordHint)parameterInfo).getHint();
                    if (hint != null && !hint.isEmpty()) {
                        passwordMessage = passwordMessage + "\n\tHint: " + hint;
                    }

                    boolean hasRecoveryEmailAddress = ((ParameterInfoPasswordHint)parameterInfo).hasRecoveryEmailAddress();
                    passwordMessage = passwordMessage + "\n\tHas recovery email: " + hasRecoveryEmailAddress;
                    String recoveryEmailAddressPattern = ((ParameterInfoPasswordHint)parameterInfo).getRecoveryEmailAddressPattern();
                    if (recoveryEmailAddressPattern != null && !recoveryEmailAddressPattern.isEmpty()) {
                        passwordMessage = passwordMessage + "\n\tRecovery email address pattern: " + recoveryEmailAddressPattern;
                    }

                    System.out.println(passwordMessage);
                    break;
                case NOTIFY_LINK:
                    String link = ((ParameterInfoNotifyLink)parameterInfo).getLink();

                    qr = link;

                    return "";
                case TERMS_OF_SERVICE:
                    TdApi.TermsOfService tos = ((ParameterInfoTermsOfService)parameterInfo).getTermsOfService();
                    question = "Terms of service:\n\t" + tos.text.text;
                    if (tos.minUserAge > 0) {
                        question = question + "\n\tMinimum user age: " + tos.minUserAge;
                    }

                    if (!tos.showPopup) {
                        System.out.println(question);
                        return "";
                    }

                    question = question + "\nPlease press enter.";
                    trim = true;
                    break;
                default:
                    question = parameter.toString();
            }

            this.bot.send_message(String.format("[%s] %s:", who, question));
            String result = null;
            try {
                result = bot.waitForCode().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return trim ? result.trim() : (String) Objects.requireNonNull(result);
        }, this.blockingExecutor);
    }

    private AuthenticationData getAuthDataNowOrNull(AuthenticationSupplier<?> authSupplier) {
        try {
            return (AuthenticationData)authSupplier.get().getNow(null);
        } catch (Throwable var3) {
            return null;
        }
    }
}
