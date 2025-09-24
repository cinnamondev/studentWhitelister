package com.github.cinnamondev.studentWhitelister.discord;

import com.fasterxml.jackson.databind.ser.Serializers;
import com.github.cinnamondev.studentWhitelister.Exceptions;
import com.github.cinnamondev.studentWhitelister.StudentWhitelister;
import com.github.cinnamondev.studentWhitelister.util.PlayerProvider;
import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.*;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.rest.util.Color;
import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.block.data.type.Snow;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.w3c.dom.Text;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.text.ParseException;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestMessage extends ReactiveEventAdapter {
    private final StudentWhitelister p;
    private final Bot b;
    public RequestMessage(StudentWhitelister p, Bot b) {
        this.p = p;
        this.b = b;
    }
    public static MessageCreateSpec message(Request r) {
        return MessageCreateSpec.builder().build()
                //.content(String.join("\n",
                //        (r.identifier() instanceof Request.Identifier.Email ? "Email: " : "Id: ") + r.identifier().toString(),
                //        "User: " + r.discordUser().getMention(),
                //        "Username: " + r.platform().toString()
                //                + "(" + (r.platform() instanceof Request.Platform.Java ? "Java" : "Bedrock") + ")")
                //)
                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                .withComponents(
                        Container.of(
                                768,
                                TextDisplay.of(769, (r.identifier() instanceof Request.Identifier.Email ? "Email: " : "Id: ") + r.identifier().toString()),
                                TextDisplay.of(770, "Discord: " + r.discordUser().getMention()),
                                TextDisplay.of(771, "Minecraft: " +  r.platform().usernameForDiscord()
                                        + " (" + (r.platform() instanceof Request.Platform.Java ? "Java" : "Bedrock") + ")"
                                )

                        ),
                        ActionRow.of(
                                Button.primary("requestBotAccept", "Accept"),
                                Button.danger("requestBotDeny", "Deny")),
                        ActionRow.of(
                                SelectMenu.of("rejectWithReason",
                                        SelectMenu.Option.of(UserMessages.NOT_IN_STUDENT_UNION, UserMessages.SU_KEY),
                                        SelectMenu.Option.of(UserMessages.NON_MANCUNIAN_EMAIL, UserMessages.EMAIL_KEY),
                                        SelectMenu.Option.of(UserMessages.OTHER_REJECTION_REASON, UserMessages.OTHER_KEY)
                                ).withPlaceholder("Other rejection reason")
                        )
                );
    }

    protected Mono<Void> editMessageAccepted(Message message) {
        Container container;
        try {
            container = (Container) message.getComponentById(768).orElseThrow();
        } catch (Exception e) {
            return Mono.error(e);
        }
        return message.edit(MessageEditSpec.builder()
                .build().withComponents(container.withColor(Color.SEA_GREEN))
        ).then();
    }

    protected Mono<Void> editMessageRejected(Message message, String reason) {
        Container container;
        try {
            container = (Container) message.getComponentById(768).orElseThrow();
        } catch (Exception e) {
            return Mono.error(e);
        }
        return message.edit(MessageEditSpec.builder()
                .build().withComponents(
                        container.withColor(Color.CINNABAR),
                        Container.of(Color.CINNABAR, TextDisplay.of("Rejected: " + reason))
                )
        ).then();
    }

    protected Mono<Void> editMessageRejected(Message message) {
        return editMessageRejected(message, UserMessages.NOT_IN_STUDENT_UNION);
    }

    protected Mono<Tuple2<Request.Platform, Member>> parseRequestMessage(Message message) {
        //TextDisplay identifierComponent;
        TextDisplay discordComponent;
        String minecraft;
        boolean isJava;
        try {
            //identifierComponent = (TextDisplay) message.getComponentById(769).orElseThrow();
            discordComponent = (TextDisplay) message.getComponentById(770).orElseThrow();
            var arr = ((TextDisplay) message.getComponentById(771).orElseThrow()).getContent()
                    .substring(11)
                    .split(" ");
            if (arr.length != 2) { return Mono.error(new InputMismatchException("invalid length")); }
            minecraft = arr[0];
            isJava = arr[1].equals("(Java)");
        } catch (Exception e) {
            return Mono.error(e);
        }

        //p.getLogger().info(minecraft);

        Mono<Tuple2<Request.Platform, Member>> request; {
            Mono<Request.Platform> platform;
            if (!isJava) {
                // FIX: ENDLESS?
                platform = Request.Platform.tryGetBedrock(p, minecraft).cast(Request.Platform.class);
            } else {
                platform = Request.Platform.tryGetJava(p, minecraft).cast(Request.Platform.class);
            }

            request = Mono.zip(
                    platform,
                    b.client.getUserById(
                            Snowflake.of(discordComponent.getContent().replaceAll("[^0-9]", ""))
                    ).flatMap(u -> u.asMember(b.guild.getId()))
            );
        }

        return request;
    }

    private static final MessageCreateSpec ACCEPTED_MESSAGE = MessageCreateSpec.builder().content("You have been accepted onto the server!").build();
    protected Mono<Void> acceptButton(ButtonInteractionEvent e) {
        if (e.getMessage().isEmpty()) { return Mono.error(new Exception("no message attributed?")); }

        return editMessageAccepted(e.getMessage().get())
                .then(parseRequestMessage(e.getMessage().get())
                        .flatMap(t -> {
                            if (b.whitelistedRole != null) {
                                return t.getT2().addRole(b.whitelistedRole).then(Mono.just(t));
                            } else { return Mono.just(t); }
                        })
                        .flatMap(t -> {
                            p.getServer().getScheduler().runTask(p, () -> PlayerProvider.whitelistProfile(t.getT1().player()));
                            p.bot.removePendingMember(t.getT1().usernameForDiscord());
                            return t.getT2().getPrivateChannel()
                                    .flatMap(c -> c.createMessage(
                                            UserMessages.acceptPrivateChannelResponse(t.getT1() instanceof Request.Platform.Java)
                                    ))
                                    .onErrorMap(Exceptions.UnreachableUserException::new);
                        }))
                //.doOnError(ex -> e.createFollowup("Failed: " +ex.getMessage()).withEphemeral(true))
                .then(e.reply("Accepted!").withEphemeral(true))
                .onErrorResume(Exceptions.UnreachableUserException.class, ex -> e.reply("Couldn't DM user!"))
                .onErrorResume(ex -> e.reply("Error :( : " + ex.getMessage()));
    }

    protected Mono<Void> denyButton(ButtonInteractionEvent e) {
        if (e.getMessage().isEmpty()) { return Mono.error(new Exception("no message attributed?")); }

        return editMessageRejected(e.getMessage().get())
                .then(parseRequestMessage(e.getMessage().get()))
                .map(t -> { b.removePendingMember(t.getT1().usernameForDiscord()); return t;})
                .flatMap(request -> request.getT2().getPrivateChannel()
                        .flatMap(c -> c.createMessage(UserMessages.rejectPrivateChannelResponse(UserMessages.SU_KEY)))
                        .onErrorMap(Exceptions.UnreachableUserException::new)
                )
                .then(e.reply("Rejected (" + UserMessages.NOT_IN_STUDENT_UNION + ")!").withEphemeral(true))
                .onErrorResume(Exceptions.UnreachableUserException.class, ex -> e.reply("Couldn't DM user!"))
                .onErrorResume(ex -> e.reply("Error :( : " + ex.getMessage()));
    }

    @Override
    public @NotNull Publisher<?> onButtonInteraction(ButtonInteractionEvent e) {
        return switch (e.getCustomId()) {
            case "requestBotAccept" -> acceptButton(e);
            case "requestBotDeny" -> denyButton(e);
            default -> Mono.empty();
        };
    }

    @Override
    public Publisher<?> onSelectMenuInteraction(SelectMenuInteractionEvent e) {
        if (!e.getCustomId().equals("rejectWithReason")) { return Mono.empty(); }
        if (e.getMessage().isEmpty()) { return Mono.error(new Exception("no message attributed?")); }

        String key = e.getValues().getFirst();
        String rejectedReason = UserMessages.keyToReason(key);

        return editMessageRejected(e.getMessage().get(), rejectedReason)
                .then(parseRequestMessage(e.getMessage().get()))
                .map(t -> { b.removePendingMember(t.getT1().usernameForDiscord()); return t;})
                .flatMap(request -> request.getT2().getPrivateChannel()
                        .flatMap(c -> c.createMessage(UserMessages.rejectPrivateChannelResponse(key)))
                        .onErrorMap(Exceptions.UnreachableUserException::new)
                )
                .then(e.reply("Rejected (" + rejectedReason +")!").withEphemeral(true))
                .onErrorResume(Exceptions.UnreachableUserException.class, ex -> e.reply("Couldn't DM user!"))
                .onErrorResume(ex -> e.reply("Error :( : " + ex.getMessage()));
    }
}
