package com.github.cinnamondev.studentWhitelister;

import com.github.cinnamondev.studentWhitelister.discord.Bot;
import com.github.cinnamondev.studentWhitelister.discord.Request;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import net.kyori.adventure.translation.GlobalTranslator;
import org.apache.commons.lang3.LocaleUtils;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BedrockForms {
    private StudentWhitelister p;
    public BedrockForms(StudentWhitelister p) {
        this.p = p;
    }

    protected static String translate(String key, String localeString) {
        Locale locale = LocaleUtils.toLocale(localeString);
        MessageFormat translated = GlobalTranslator.translator().translate(key, LocaleUtils.toLocale(localeString));
        if (translated != null) {
            return translated.format(null);
        } else { return key; } // fall back to translation key provided.
    }

    private final static CustomForm.Builder JOIN_FORM =
            CustomForm.builder()
                    .translator(BedrockForms::translate)
                    .title("minecraft.bedrock.form-title")
                    .input("minecraft.bedrock.identifier", "11095134")
                    .input("minecraft.bedrock.discord-uname", "jpromptig");

    public static CompletableFuture<Request> showForm(StudentWhitelister p, FloodgatePlayer floodgate, PlayerConfigurationConnection connection, int attempts) {
        return showJoinForm(p, floodgate, connection)
                .orTimeout(5, TimeUnit.MINUTES)
                .exceptionallyCompose(ex -> showErrorForm(p, floodgate, connection, attempts > 3, ex)
                        .thenCompose(disconnect -> disconnect
                                ? CompletableFuture.failedFuture(new Exceptions.CancelledDialogException())
                                : showForm(p, floodgate, connection, attempts)
                        )
                );
    }
    public static CompletableFuture<Request> showJoinForm(StudentWhitelister p, FloodgatePlayer floodgate, PlayerConfigurationConnection connection) {
        CompletableFuture<Request> future = new CompletableFuture<>();
        floodgate.sendForm(JOIN_FORM
                .closedOrInvalidResultHandler(() -> future.completeExceptionally(new Exceptions.CancelledDialogException()))
                .validResultHandler(response -> formResponseHandler(p, future, floodgate, connection, response))
                .build()
        );

        return future;
    }

    protected static void formResponseHandler(StudentWhitelister p, CompletableFuture<Request> future, FloodgatePlayer player, PlayerConfigurationConnection connection, CustomFormResponse formResponse) {
        try {
            Request.Identifier identifier = Request.Identifier.parseFrom(Objects.requireNonNull(formResponse.next()));

            Request.Platform platform = new Request.Platform.Bedrock(
                    p.getServer().getOfflinePlayer(connection.getProfile().getId()),
                    player.getUsername()
            );

            Request.makeWithDiscordUser(
                    p.bot,
                    formResponse.next(),
                    platform,
                    identifier
            ).subscribe(future::complete, future::completeExceptionally);
        } catch (Exception e) {
            future.completeExceptionally(e);
            return;
        }

    }

    private final static ModalForm.Builder BASIC_ERROR_FORM =
            ModalForm.builder()
                    .translator(BedrockForms::translate)
                    .title("minecraft.bedrock.error-title")
                    .button2("minecraft.bedrock.disconnect");

    public static CompletableFuture<Boolean> showErrorForm(StudentWhitelister p, FloodgatePlayer player, PlayerConfigurationConnection connection, boolean retry, Throwable e) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        String content;

        if (e instanceof Exceptions.DiscordValidationException) {
            content = retry ? "minecraft.bedrock.form-failure.no-discord" : "minecraft.bedrock.form-failure.no-discord-no-attempts";
        } else if (e instanceof Exceptions.IdentifierValidationException) {
            content = retry ? "minecraft.bedrock.form-failure.no-identifier" : "minecraft.bedrock.form-failure.no-identifier-no-attempts";
        }
        else {
            content = e.getMessage() + "\n" + (retry ? "You are out of attempts for this session.": "");
        }
        player.sendForm(BASIC_ERROR_FORM.content(content)
                .button1(retry ? "minecraft.bedrock.try-again" : "minecraft.bedrock.disconnect")
                .validResultHandler(response -> result.complete(response.clickedButtonText().equals("minecraft.bedrock.disconnect")))
                .closedOrInvalidResultHandler(resp -> result.complete(false))
                .build()
        );
        return result;
    }
}
