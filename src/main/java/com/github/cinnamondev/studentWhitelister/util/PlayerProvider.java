package com.github.cinnamondev.studentWhitelister.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.cinnamondev.studentWhitelister.Exceptions;
import com.github.cinnamondev.studentWhitelister.StudentWhitelister;
import com.mojang.authlib.GameProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class PlayerProvider {
    protected static Constructor<OfflinePlayer> OFFLINE_PLAYER_CONSTRUCTOR;

    static {
        try { // get bukkit internal classes
            Class<?> serverClazz = Class.forName("org.bukkit.craftbukkit.CraftServer");
            Class<?> playerClazz = Class.forName("org.bukkit.craftbukkit.CraftOfflinePlayer");
            OFFLINE_PLAYER_CONSTRUCTOR = (Constructor<OfflinePlayer>) playerClazz
                    .getDeclaredConstructor(serverClazz, GameProfile.class);
            OFFLINE_PLAYER_CONSTRUCTOR.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    // PLAYER MUST BE COMPLETE.
    public static OfflinePlayer profileToPlayer(PlayerProfile profile) {
        if (profile.getId() == null || profile.getName() == null) { throw new IllegalArgumentException("cannot be incomplete"); }
        try {
            return OFFLINE_PLAYER_CONSTRUCTOR.newInstance(Bukkit.getServer(), new GameProfile(profile.getId(), profile.getName()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static Mono<OfflinePlayer> getExistingOfflinePlayer(Plugin p, String username) {
        try {
            return Mono.just(p.getServer().getOfflinePlayer(username))
                    .filter(player -> player.getUniqueId().version() != 3);
        } catch (Exception e) {
            return Mono.error(new Exceptions.InvalidMinecraftUser(e));
        }
    }

    /**
     * get java player, returning an error if failed to find them. (instead of returning an empty)
     * @param p
     * @param username
     * @return offline player
     */
    public static Mono<OfflinePlayer> getJavaPlayer(Plugin p, String username) {
        return getExistingOfflinePlayer(p, username)
                .switchIfEmpty(Mono.error(new Exceptions.InvalidMinecraftUser("non existent java player")));
    }

    protected static String gamertagToMinecraft(String gt) {
        String gamertag = gt.trim().replace(' ', '_');
        int len = Math.min(gamertag.length(), 16 - StudentWhitelister.getFloodgatePrefix().length());
        return StudentWhitelister.getFloodgatePrefix() + gamertag.substring(0, len);
    }
    /**
     * get bedrock player
     * @param gamertag whether to interpret username as gamertag
     * @return offline player corresponding to bedrock player
     */
    public static Mono<PlayerProfile> getBedrockPlayer(Plugin p, String gamertag) {
        if (!StudentWhitelister.isFloodgateAvailable()) { return Mono.error(new UnsupportedOperationException("floodgate is unavailable.")); }

        String java = gamertagToMinecraft(gamertag);
        // try to get it from the server and if they dont have it then we will try to get it from elsewhere
        return Mono.fromSupplier(() -> p.getServer().getOfflinePlayerIfCached(java))
                .map(offlinePlayer -> p.getServer().createProfile(offlinePlayer.getUniqueId(), java))
                .switchIfEmpty(Mono.fromFuture(FloodgateApi.getInstance().getUuidFor(gamertag))
                        .switchIfEmpty(Mono.error(new Exceptions.InvalidMinecraftUser("non existent bedrock player")))
                        .map(uuid -> p.getServer().createProfile(uuid, java))
                );
    }

    public static void whitelistProfile(PlayerProfile profile) {
        profileToPlayer(profile).setWhitelisted(true);
    }
}
