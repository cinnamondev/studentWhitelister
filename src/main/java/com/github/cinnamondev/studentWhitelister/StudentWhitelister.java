package com.github.cinnamondev.studentWhitelister;

import com.github.cinnamondev.studentWhitelister.discord.Bot;
import com.github.cinnamondev.studentWhitelister.util.PlayerProvider;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ServerLinks;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.sisu.bean.LifecycleManager;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;

public final class StudentWhitelister extends JavaPlugin {
    private PlayerListener whitelistWatcher;

    private Command command;
    public Bot bot;
    // These values are initialized in the bootstrapper.
    public static boolean BOOTSTRAP_SUCCESSFUL = false;
    public static String STUDENT_UNION_LINK;
    public static String DISCORD_INVITE;

    public static Component SU_BTN;
    public static Component DISCORD_BTN;
    public static Component CANCELLATION_MESSAGE;
    public static Component DIALOG_MESSAGE;

    // called in bootstrapper
    public static void initializeConfigItems(FileConfiguration c) {
        STUDENT_UNION_LINK = c.getString("urls.student-union", "https://su-not-specified.example.org");
        DISCORD_INVITE = c.getString("urls.discord-server", "https://discord-not-specified.example.org");

        SU_BTN = Component.text("Student Union")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(STUDENT_UNION_LINK));
        DISCORD_BTN = Component.text("Discord")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(DISCORD_INVITE));

        DIALOG_MESSAGE = Component.text("We require everyone to register with the ").append(SU_BTN)
                .append(Component.text(", and be a part of our ")).append(DISCORD_BTN)
                .append(Component.text(" before playing. Please do the above (if you haven't already) then provide your " +
                        "details below." + "This is only done for the purpose of verifying you are a member of our " +
                        "society, and please be aware that we have to manually check these requests " +
                        "(you should be notified when its done, if you have already submitted once, please wait.)"));


        CANCELLATION_MESSAGE = Component.text("Don't forget to join our ").append(DISCORD_BTN)
                .append(Component.text(", and register with the ")).append(SU_BTN)
                .append(Component.text(" if you haven't already! See you soon!"));
    }

    @Override
    public void onEnable() {
        PlayerProvider.checkForFloodgate();
        saveDefaultConfig();
        if (!BOOTSTRAP_SUCCESSFUL) {
            getLogger().warning("Plugin startup will not progress, please set up your config :)");
            return;
        }

        this.whitelistWatcher = new PlayerListener(this);
        this.command = new Command(this);

        try { // try and add discord/student links;
            URI discordUri = URI.create(DISCORD_INVITE);
            URI studentUri = URI.create(STUDENT_UNION_LINK);
            getServer().getServerLinks().addLink(ServerLinks.Type.FORUMS, discordUri);
            getServer().getServerLinks().addLink(ServerLinks.Type.WEBSITE, studentUri);
        } catch (Exception e) { // not really critical we do this so just supress whatever
            getLogger().warning(e.getMessage());
        }

        startBot().subscribe(ok -> {
            getServer().getScheduler().runTask(this,
                    () -> getServer().getPluginManager().registerEvents(whitelistWatcher, this)
            );
        });
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, c -> {
            c.registrar().register(Command.command(this));
        });
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        bot.close().block();
    }

    public Mono<Void> reload() {
        initializeConfigItems(getConfig());
        return bot.close().then(startBot());
    }

    public Mono<Void> startBot() {
        // i cant seem to get the bot to come back..
        return Bot.startBot(this)
                .publishOn(Schedulers.parallel())
                .doOnNext(b -> this.bot = b)
                .then();
    }

}
