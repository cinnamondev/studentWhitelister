package com.github.cinnamondev.studentWhitelister.discord;

import com.github.cinnamondev.studentWhitelister.StudentWhitelister;
import com.github.cinnamondev.studentWhitelister.Exceptions;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.retriever.EntityRetrievalStrategy;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import org.bukkit.configuration.InvalidConfigurationException;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class Bot {
    public GatewayDiscordClient client;
    private final Guild guild;
    private final TextChannel channel;
    private Snowflake whitelistedRole = null;
    private RequestMessage requestMessage;
    private WhitelistCommand command;
    protected Bot(StudentWhitelister p, GatewayDiscordClient client, Guild guild, TextChannel channel, Snowflake whitelistedRole) {
        this.client = client;
        this.guild = guild;
        this.channel = channel;
        this.whitelistedRole = whitelistedRole;
        this.requestMessage = new RequestMessage(p, this);
        this.command = new WhitelistCommand(p);

        if (p.getConfig().getBoolean("discord.respond-to-messages", false)) { // primary specific
            client.getRestClient().getApplicationId()
                    .flatMap(id -> client.getRestClient().getApplicationService().createGuildApplicationCommand(
                            id,
                            guild.getId().asLong(),
                            WhitelistCommand.command
                    ))
                    .subscribe();

            client.on(command).subscribe(ok -> {}, ex -> p.getLogger().warning(ex.getMessage()));
        }

        client.on(requestMessage).subscribe(ok -> {}, ex -> p.getLogger().warning(ex.getMessage()));
    }

    public static Mono<Bot> startBot(StudentWhitelister p) {
        String secret = p.getConfig().getString("discord.secret");
        long guildId = p.getConfig().getLong("discord.guild", -1);
        long channelId = p.getConfig().getLong("discord.channel", -1);
        long roleId = p.getConfig().getLong("discord.role", -1);
        // null check
        if (secret == null || secret.isEmpty()) { return Mono.error(new InvalidConfigurationException("Bot secret is null or empty!")); }
        if (guildId == -1) { return Mono.error(new InvalidConfigurationException("Bot guild is null or empty!")); }
        if (channelId == -1) {  return Mono.error(new InvalidConfigurationException("Bot channel is null or empty!")); }
        var gateway = DiscordClient.create(secret)
                .gateway()
                .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS))
                .login()
                .doOnError(ex -> p.getLogger().warning(ex.getMessage()))
                .retry(2);


        return gateway.flatMap(g -> {
            Mono<Guild> guildMono = g.getGuildById(Snowflake.of(guildId));
            return Mono.zip(
                    Mono.just(g),
                    guildMono,
                    guildMono.flatMap(guild -> guild.getChannelById(Snowflake.of(channelId)))
            );
        }).map(t -> new Bot(p,
                t.getT1(),
                t.getT2(),
                (TextChannel) t.getT3(),
                roleId != -1 ? Snowflake.of(roleId) : null
        ));
    }

    protected Mono<Void> giveMemberWhitelistedRole(Member member) {
        if (whitelistedRole == null) { return Mono.error(new InvalidConfigurationException("no whitelisted role!")); }
        return member.addRole(whitelistedRole);
    }



    protected Mono<Member> getGuildMemberByUsername(Guild g, String username) {
        if (username == null || username.isEmpty()) {
            return Mono.error(new Exceptions.DiscordValidationException("username is null or empty!"));
        }

        return g.getMembers(EntityRetrievalStrategy.STORE_FALLBACK_REST)
                .filter(m -> !m.isBot())
                .filter(m -> m.getUsername().equals(username))
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new Exceptions.DiscordValidationException("user not found")));
    }

    public Mono<Member> getGuildMemberByUsername(String username) {
        return getGuildMemberByUsername(this.guild, username);
    }

    public Mono<Void> makeWhitelistRequest(Request request) {
        return channel.createMessage(RequestMessage.message(request)).then();
    }
}
