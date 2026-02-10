package com.github.cinnamondev.studentWhitelister.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.cinnamondev.studentWhitelister.Exceptions;
import com.github.cinnamondev.studentWhitelister.StudentWhitelister;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import net.minecraft.server.players.NameAndId;
import org.geysermc.floodgate.api.FloodgateApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.util.Optional;

public class PlayerProvider {
    private static final Logger log = LoggerFactory.getLogger(PlayerProvider.class);
    protected static Constructor<OfflinePlayer> OFFLINE_PLAYER_CONSTRUCTOR;
    static {
        try { // get bukkit internal classes
            Class<?> playerClazz = Class.forName("org.bukkit.craftbukkit.CraftOfflinePlayer");
            //new NameAndId(UUID.randomUUID(), "");
            OFFLINE_PLAYER_CONSTRUCTOR = (Constructor<OfflinePlayer>) playerClazz
                    .getDeclaredConstructor(
                            org.bukkit.craftbukkit.CraftServer.class,
                            NameAndId.class
                    );
            OFFLINE_PLAYER_CONSTRUCTOR.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // PLAYER MUST BE COMPLETE.
    public static OfflinePlayer profileToPlayer(PlayerProfile profile) {
        if (profile.getId() == null || profile.getName() == null) { throw new IllegalArgumentException("cannot be incomplete"); }
        try {
            return OFFLINE_PLAYER_CONSTRUCTOR.newInstance(Bukkit.getServer(), new NameAndId(profile.getId(), profile.getName()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Mono<PlayerProfile> getExistingOfflinePlayer(Plugin p, String username) {
        return Optional.ofNullable(p.getServer().getOfflinePlayerIfCached(username)) // this is more flexible than just createProfile as it will
                .map(OfflinePlayer::getPlayerProfile) // pull from disk(?) for offlineplayers
                .map(Mono::just).orElse(Mono.empty())
                .switchIfEmpty(Mono.fromFuture(p.getServer().createProfile(username).update())
                        .flatMap(profile -> profile.getId() != null ? Mono.just(profile)
                                : Mono.error(new Exceptions.InvalidMinecraftUser("user " + username + " doesnt exist :("))
                        ));
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
        return Optional.ofNullable(p.getServer().getOfflinePlayerIfCached(java))
                .map(OfflinePlayer::getPlayerProfile)
                .map(Mono::just).orElse(Mono.empty()) // turn it into a mono type
                .switchIfEmpty(Mono.fromFuture(FloodgateApi.getInstance().getUuidFor(gamertag)) // if we dont emit a value get it from the far away lands.
                        .switchIfEmpty(Mono.error(new Exceptions.InvalidMinecraftUser("non existent bedrock player")))
                        .map(uuid -> p.getServer().createProfile(uuid, java))
                );
    }

    public static void whitelistProfile(PlayerProfile profile) {
        profileToPlayer(profile).setWhitelisted(true);
    }
}
