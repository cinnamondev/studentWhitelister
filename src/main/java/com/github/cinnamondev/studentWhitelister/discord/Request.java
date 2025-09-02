package com.github.cinnamondev.studentWhitelister.discord;

import com.github.cinnamondev.studentWhitelister.Exceptions;
import com.github.cinnamondev.studentWhitelister.util.PlayerProvider;
import discord4j.core.object.entity.User;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public record Request(Platform platform, User discordUser, Identifier identifier) {
    public static String requestInfo(Request request) {
        return "discord: " + request.discordUser.getUsername() + "\n" +
        "minecraft: " + request.platform.player().getName() + "\n" +
        "identifier" + request.identifier().toString();
    }

    public interface Identifier {
        record Email(String email) implements Identifier {
            @Override public @NotNull String toString() { return email; }
        }
        record Id(int id) implements Identifier {
            @Override public @NotNull String toString() { return String.valueOf(id); }
        }

        String toString();
        static Identifier parseFrom(String identifier) {
            if (identifier == null || identifier.isEmpty()) {
                throw new Exceptions.IdentifierValidationException("empty identifier!");
            }
            identifier = identifier.strip();
            if (NumberUtils.isNumber(identifier)) {
                return new Id(Integer.parseInt(identifier));
            } else if (EmailValidator.getInstance(false, false).isValid(identifier)) {
                return new Email(identifier);
            } else {
                throw new Exceptions.IdentifierValidationException("Not a valid identifier: " + identifier);
            }
        }
    }
    public interface Platform {
        record Java(OfflinePlayer player) implements Platform {
            @Override public String usernameForDiscord() { return player.getName(); }
        }

        record Bedrock(OfflinePlayer player, String gamerTag) implements Platform {
            @Override public String usernameForDiscord() { return gamerTag; }
        }

        String usernameForDiscord();
        OfflinePlayer player();

        static Mono<Platform.Bedrock> tryGetBedrock(Plugin plugin, String gamertag) {
            return PlayerProvider.getBedrockPlayer(plugin, gamertag)
                    .map(player -> new Bedrock(player, gamertag));
        }

        static Mono<Platform.Java> tryGetJava(Plugin plugin, String unparsedUsername) {
            return PlayerProvider.getJavaPlayer(plugin, unparsedUsername)
                    .map(Platform.Java::new);
        }
    }
    public static Mono<Request> makeWithDiscordUser(Bot bot, String discordUser, Platform platform, Identifier identifier) {
        return bot.getGuildMemberByUsername(discordUser)
                .map(user -> new Request(platform, user, identifier));
    }
}