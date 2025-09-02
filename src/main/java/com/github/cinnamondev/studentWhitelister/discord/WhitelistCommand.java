package com.github.cinnamondev.studentWhitelister.discord;

import com.github.cinnamondev.studentWhitelister.Exceptions;
import com.github.cinnamondev.studentWhitelister.StudentWhitelister;
import com.github.cinnamondev.studentWhitelister.util.PlayerProvider;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.List;

public class WhitelistCommand extends ReactiveEventAdapter {
    private final StudentWhitelister p;
    public WhitelistCommand(StudentWhitelister p) {
        this.p = p;
    }

    public static ApplicationCommandRequest command = ApplicationCommandRequest.builder()
            .name("whitelist")
            .description("get whitelisted")
            .addOption(ApplicationCommandOptionData.builder()
                    .name("identifier")
                    .description("student e-mail or id number")
                    .type(ApplicationCommandOption.Type.STRING.getValue())
                    .required(true)
                    .maxLength(254)
                    .build()
            )
            .addOption(ApplicationCommandOptionData.builder()
                    .name("username")
                    .description("minecraft username")
                    .type(ApplicationCommandOption.Type.STRING.getValue())
                    .required(true)
                    .maxLength(32)
                    .build()
            )
            .addOption(ApplicationCommandOptionData.builder() // ???
                    .name("platform")
                    .description("do you play java or bedrock?")
                    .type(ApplicationCommandOption.Type.STRING.getValue())
                    .required(false)
                    .addAllChoices(List.of(
                            ApplicationCommandOptionChoiceData.builder().name("Java").value("Java").build(),
                            ApplicationCommandOptionChoiceData.builder().name("Bedrock").value("Bedrock").build()
                    ))
                    .build()
            ).build();

    @Override
    public Publisher<?> onChatInputInteraction(ChatInputInteractionEvent e) {
        if (!e.getCommandName().equals("whitelist")) { return Mono.empty(); }

        String identifierString;
        String platformString;
        boolean isJava; {
            String str = e.getOptionAsString("platform").orElse("Java");
            if (str.equalsIgnoreCase("Java")) {
                isJava = true;
            } else if (str.equalsIgnoreCase("Bedrock")) {
                isJava = false;
            } else {
                return Mono.error(new IllegalArgumentException("unknown choice? weird work around"));
            }
        }

        try {
            identifierString = e.getOptionAsString("identifier").orElseThrow();
            platformString = e.getOptionAsString("username").orElseThrow();
        } catch (Exception ex) {
            return Mono.error(ex);
        }

        Mono<Request.Platform> platform;
        if (!isJava) {
            platform = Request.Platform.tryGetBedrock(p, platformString).cast(Request.Platform.class);
        } else {
            platform = Request.Platform.tryGetJava(p, platformString).cast(Request.Platform.class);
        }

        return platform.map(p -> new Request(p, e.getUser(), Request.Identifier.parseFrom(identifierString)))
                .flatMap(r -> p.bot.makeWhitelistRequest(r))
                .then(e.reply("Done!").withEphemeral(true))
                .onErrorResume(Exceptions.IdentifierValidationException.class,
                        ex -> e.reply("You need to provide a full e-mail or student id!").withEphemeral(true))
                .onErrorResume(ex -> e.reply("Error :( " + ex.getMessage()).withEphemeral(true));
    }
}
