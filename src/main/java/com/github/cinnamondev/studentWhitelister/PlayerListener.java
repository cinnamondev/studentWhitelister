package com.github.cinnamondev.studentWhitelister;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.cinnamondev.studentWhitelister.discord.Request;
import com.github.cinnamondev.studentWhitelister.util.PlayerProvider;
import com.google.common.util.concurrent.Runnables;
import io.papermc.paper.connection.PlayerCommonConnection;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.tuple.MutablePair;
import org.bukkit.block.Bed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class PlayerListener implements Listener {
    private final StudentWhitelister p;
    public PlayerListener(StudentWhitelister p) {
        this.p = p;
    }

    private final HashSet<UUID> toBeChecked = new HashSet<>();
    private final HashMap<PlayerCommonConnection, CompletableFuture<Request>> activePlayers = new HashMap<>();
    private final Dialog dialog = RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG).get(Key.key("subot:whitelist_dialog"));
    @EventHandler
    public void playerConfiguration(AsyncPlayerConnectionConfigureEvent e) {
        if (!toBeChecked.contains(e.getConnection().getProfile().getId())) {
            e.getConnection().completeReconfiguration(); // player is whitelisted, we don't need to hold them up.
            return;
        } else { // locked in
            toBeChecked.remove(e.getConnection().getProfile().getId());
        }

        if (e.getConnection().getProfile().getName().startsWith(PlayerProvider.getFloodgatePrefix())) { // player is bedrock...
            if (!PlayerProvider.HAS_FLOODGATE) { // weird state
                e.getConnection().disconnect(Component.text("bedrock player connected, but we dont have access to floodgate..."));
                return;
            }
        }
        // request = ...
        activePlayers.put(e.getConnection(), new CompletableFuture<>());
        e.getConnection().getAudience().showDialog(dialog);


        int attempts = 0;
        while (attempts <= 3) {
            try {
                attempts++;
                // NOTE: bedrocks loop is quite different - we shouldnt expect to
                // leave the following line until we get a successful result or the user has
                // cancelled the operation / timed out. all attempt handling is enclosed within
                // BedrockForms :) it also means we dont need to worry about these other exceptions.
                // I HOPE.
                Request completedRequest = activePlayers.get(e.getConnection()) // get request from pairs-- it might have been swapped out in a previous attempt
                        .get(10, TimeUnit.MINUTES);
                p.bot.makeWhitelistRequest(completedRequest) // non blocking, if we have an issue on our part then uh. Log it.
                        .subscribe(ok -> {}, ex -> {
                            p.getLogger().warning(Request.requestInfo(completedRequest));
                            p.getLogger().warning(ex.getMessage());
                        });
                //p.getLogger().info(
                //        "user " + completedRequest.discordUser().getUsername()
                //                + " username" + completedRequest.platform().player().getName()
                //                + " identifier" + completedRequest.identifier().toString()
                //);
                attempts = 5; // we should be 'done' and skip over the error dialog if all goes well.
                // the moment anythign goes wrong, it goes into one of these catches, we will probably disconnect or
                // show an alternative dialog to the user.
            } catch (TimeoutException ex) {
                e.getConnection().disconnect(Component.text("You are out of attempts!"));
                return;
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof Exceptions.CancelledDialogException) {
                    e.getConnection().disconnect(StudentWhitelister.CANCELLATION_MESSAGE);
                    return;
                } else {
                    /// ////////////////////
                    ///// BODGE ALERT!!
                    /// ///////////////////
                    // generic handler retry dialog, will only let them go back to the normal track if completed succesfully.
                    activePlayers.put(e.getConnection(), new CompletableFuture<>());
                    e.getConnection().getAudience().showDialog(
                            Dialogs.userErrorDialog(ex, attempts < 3) // on the 4th one the only available option should be to DC
                            // (cancelled dialog exception :))
                    );
                }
            } catch (Exceptions.CancelledDialogException ex) {
                e.getConnection().disconnect(StudentWhitelister.CANCELLATION_MESSAGE);
                return;
            } catch (Exception ex) {
                // generic handler retry dialog, will only let them go back to the normal track if completed succesfully.
                activePlayers.put(e.getConnection(), new CompletableFuture<>());
                e.getConnection().getAudience().showDialog(
                        Dialogs.userErrorDialog(ex, attempts < 3) // on the 4th one the only available option should be to DC
                        // (cancelled dialog exception :))
                );
            }
        }

        activePlayers.remove(e.getConnection());
        e.getConnection().disconnect(StudentWhitelister.CANCELLATION_MESSAGE);
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void showFormIfUnwhitelisted(ProfileWhitelistVerifyEvent e) {
        if (!e.isWhitelisted()) {
            if (activePlayers.size() >= 50) { return; } // limit them!
            // ban logic goes here
            // has PLAYER __retried__ N times in the last Y time period TODO
            toBeChecked.add(e.getPlayerProfile().getId());
            e.setWhitelisted(true);
        }
        // TODO:
        // spam protection (check how long ago a connection last attempted whitelist process, if excessive or idle we will progressively increase the fail time...)
    }

    ///  DIALOG handling!
    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent e) {
        Key key = e.getIdentifier();
        if (!key.namespace().equals("subot")) { return; }


        // we SHOULD be in the configuration phase but maybe not in all contexts we will be.
        // so lets pull our profile and audience from the relevant parts.
        PlayerProfile profile;
        Audience audience;
        if (e.getCommonConnection() instanceof PlayerConfigurationConnection connection) {
            profile = connection.getProfile();
            audience = connection.getAudience();
        } else if (e.getCommonConnection() instanceof PlayerGameConnection connection) { // future support
            p.getLogger().info("attempted whitelist attempt in game phase...");
            profile = connection.getPlayer().getPlayerProfile();
            audience = connection.getPlayer();
            //return;
        } else { return; } // we shouldnt get here..


        if (key.equals(Dialogs.GENERIC_CANCEL_BUTTON_KEY)) { // player has hit a disconnect type button
            activePlayers.computeIfPresent(
                    e.getCommonConnection(),
                    (c,f) -> {
                        f.completeExceptionally(new Exceptions.CancelledDialogException());
                        return f;
                    }
            );
        } else if (key.equals(Dialogs.RETRY_BUTTON_KEY)) { // player has hit 'retry'
            audience.closeDialog();
            audience.showDialog(dialog);
        } else if (key.equals(Dialogs.REGISTER_BUTTON_KEY)) { // request time!
            DialogResponseView r = e.getDialogResponseView(); assert r != null;
            String discordStr = r.getText("discord");
            String identifierStr = r.getText("identifier");


            var future = activePlayers.get(e.getCommonConnection());

            p.bot.getGuildMemberByUsername(discordStr)
                    .map(m -> {
                        Request.Platform platform;
                        // SO it turned out i don't have to do all the other stuff with BedrockForms! it just...
                        // does it for you... SMH....
                        if (PlayerProvider.HAS_FLOODGATE && FloodgateApi.getInstance().isFloodgatePlayer(profile.getId())) {
                            var gamertag = FloodgateApi.getInstance().getPlayer(profile.getId()).getUsername();
                            platform = new Request.Platform.Bedrock(PlayerProvider.profileToPlayer(profile), gamertag);
                        } else {
                            platform = new Request.Platform.Java(p.getServer().getOfflinePlayer(profile.getId()));
                        }
                        return new Request(
                                platform,
                                m,
                                Request.Identifier.parseFrom(identifierStr)
                        );
                    }).subscribe(
                            future::complete,
                            future::completeExceptionally
                    );
        }
    }
}
