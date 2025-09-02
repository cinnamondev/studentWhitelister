package com.github.cinnamondev.studentWhitelister.util;

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
    private static final Logger log = LoggerFactory.getLogger(PlayerProvider.class);
    public static boolean HAS_FLOODGATE = false;
    protected static String FLOODGATE_PREFIX = ".";
    public static String getFloodgatePrefix() { return FLOODGATE_PREFIX; }
    protected static Constructor<OfflinePlayer> OFFLINE_PLAYER_CONSTRUCTOR;
    static {
        try { // check if we have floodgate
            Class<?> floodgateClazz = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            HAS_FLOODGATE = true;
        } catch (ClassNotFoundException ignored) {
            log.info("Floodgate not found");
        } finally {
            if (HAS_FLOODGATE) { FLOODGATE_PREFIX = FloodgateApi.getInstance().getPlayerPrefix(); }
        }

        try { // get bukkit internal classes
            Class<?> serverClazz = Class.forName("org.bukkit.craftbukkit.CraftServer");
            Class<?> playerClazz = Class.forName("org.bukkit.craftbukkit.CraftOfflinePlayer");
            OFFLINE_PLAYER_CONSTRUCTOR = (Constructor<OfflinePlayer>) playerClazz
                    .getDeclaredConstructor(serverClazz, GameProfile.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    protected static OfflinePlayer createOfflinePlayer(String username, UUID uuid) {
        try {
            return OFFLINE_PLAYER_CONSTRUCTOR.newInstance(Bukkit.getServer(), new GameProfile(uuid, username));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static Mono<OfflinePlayer> getExistingOfflinePlayer(Plugin p, String username) {
        return Mono.just(p.getServer().getOfflinePlayer(username))
                .filter(player -> player.getUniqueId().version() != 3);
    }

    /**
     * get java player, returning an error if failed to find them. (instead of returning an empty)
     * @param p
     * @param username
     * @return offline player
     */
    public static Mono<OfflinePlayer> getJavaPlayer(Plugin p, String username) {
        return getExistingOfflinePlayer(p, username)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("non existent player")));
    }

    protected static String gamertagToMinecraft(String gt) {
        String gamertag = gt.trim().replace(' ', '_');
        int len = Math.min(gamertag.length(), 15);
        return "." + gamertag.substring(0, len);
    }
    /**
     * get bedrock player
     * @param name username (interpreted as gamertag if "gamertag" is true, else it would be a java username i.e. ".ImShama")
     * @param gamertag whether to interpret username as gamertag
     * @return offline player corresponding to bedrock player
     */
    public static Mono<OfflinePlayer> getBedrockPlayer(Plugin p, String name, boolean gamertag) {
        if (!HAS_FLOODGATE) { return Mono.error(new UnsupportedOperationException("floodgate is unavailable.")); }

        String java = gamertag ?  gamertagToMinecraft(name) : name;
        // try to get it from the server and if they dont have it then we will try to get it from elsewhere
        return getExistingOfflinePlayer(p, name).switchIfEmpty(
                Mono.fromFuture(FloodgateApi.getInstance().getUuidFor(name))
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("non existent player")))
                        .map(uuid -> createOfflinePlayer(java, uuid))
        );
    }
}
