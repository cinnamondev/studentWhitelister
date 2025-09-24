package com.github.cinnamondev.studentWhitelister.discord;

import com.github.cinnamondev.studentWhitelister.Exceptions;
import com.github.cinnamondev.studentWhitelister.StudentWhitelister;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.component.*;
import discord4j.core.object.entity.Member;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.core.spec.MessageCreateSpec;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.Collections;
import java.util.InputMismatchException;
import java.util.List;

public class Modal extends ReactiveEventAdapter {
    private StudentWhitelister p;
    public Modal(StudentWhitelister p) { this.p = p; }

    protected static MessageCreateSpec message = MessageCreateSpec.builder().build().withComponents(List.of(
            ActionRow.of(Button.primary("getWhitelistedModal", "Get whitelisted!"))
    ));

    public static final String MODAL_ID = "subot-whitelist-modal";
    public static final InteractionPresentModalSpec modal = InteractionPresentModalSpec.builder()
            .title("Get whitelisted!")
            .customId(MODAL_ID)
            .addAllComponents(List.of(
                    Label.of("Minecraft username", TextInput.small("minecraftUsername")),
                    Label.of("Student ID / Email", TextInput.small("identifier")),
                    Label.of("Platform", SelectMenu.of("platform",
                            SelectMenu.Option.of("Java", "java").withDefault(true),
                            SelectMenu.Option.of("Bedrock", "bedrock")
                    ))
            ))
            .build();


    @Override
    public @NotNull Publisher<?> onModalSubmitInteraction(ModalSubmitInteractionEvent e) {
        if (!e.getCustomId().equals(MODAL_ID)) { return Mono.empty(); }
        TextInput minecraftComponent = null;
        TextInput identifierComponent = null;

        for (TextInput component : e.getComponents(TextInput.class)) {
            switch (component.getCustomId()) {
                case "minecraftUsername" -> minecraftComponent = component;
                case "identifier" -> identifierComponent = component;
            }
        }
        if (minecraftComponent == null || identifierComponent == null) { return Mono.empty(); }

        var selectMenu = e.getComponents(SelectMenu.class)
                .getFirst()
                .getValues()
                .orElse(Collections.emptyList())
                .getFirst()
                .toLowerCase();

        Mono<Request.Platform> platform = switch (selectMenu) {
            case "java" -> Request.Platform.tryGetJava(p, minecraftComponent.getValue().orElse(null)).cast(Request.Platform.class);
            case "bedrock" -> Request.Platform.tryGetBedrock(p, minecraftComponent.getValue().orElse(null)).cast(Request.Platform.class);
            default -> throw new IllegalStateException("Unexpected value: " + selectMenu);
        };

        Request.Identifier identifer;
        try {
            identifer = Request.Identifier.parseFrom(identifierComponent.getValue().orElse(null));
        } catch (Exceptions.IdentifierValidationException ex) {
            return e.reply("Invalid Identifier / Email!")
                    .withComponents(List.of(
                            ActionRow.of(Button.primary("getWhitelistedModal", "Try Again"))
                    ))
                    .withEphemeral(true);
        }

        return platform.map(p -> new Request(p, e.getUser(), identifer))
                .flatMap(r -> p.bot.makeWhitelistRequest(r))
                .then(e.reply("Thank you!").withEphemeral(true))
                .onErrorResume(Exceptions.InvalidMinecraftUser.class,
                        ex -> e.reply("Could not find a player with that username! (Double check your spelling & platform)")
                                .withEphemeral(true)
                                .withComponents(List.of(
                                        ActionRow.of(Button.primary("getWhitelistedModal", "Try Again"))
                                ))
                )
                .onErrorResume(ex -> { // generic error?
                    p.getLogger().warning(ex.getMessage());
                    return e.reply("Other error occured! Please try again or ask committee if it occurs again")
                            .withEphemeral(true)
                            .withComponents(List.of(
                                    ActionRow.of(Button.primary("getWhitelistedModal", "Try Again"))
                            ));
                });
    }

    @Override
    public Publisher<?> onButtonInteraction(ButtonInteractionEvent e) {
        if (e.getCustomId().equals("getWhitelistedModal")) {
            return e.presentModal(modal);
        } else {
            return Mono.empty();
        }
    }
}
