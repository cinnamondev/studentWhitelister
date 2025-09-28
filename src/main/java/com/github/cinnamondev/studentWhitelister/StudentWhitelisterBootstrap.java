package com.github.cinnamondev.studentWhitelister;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class StudentWhitelisterBootstrap implements PluginBootstrap {
    private static final Logger log = LoggerFactory.getLogger(StudentWhitelisterBootstrap.class);
    public static TypedKey<Dialog> DIALOG_KEY = DialogKeys.create(Key.key("subot:whitelist_dialog"));
    @Override
    public void bootstrap(BootstrapContext context) {
        log.info(context.getDataDirectory().toString());
        File file = context.getDataDirectory().resolve("config.yml").toFile();
        if (file.exists()) {
            log.info("Loading config.yml");
            YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
            StudentWhitelister.initializeConfigItems(c);

            context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose()
                    .newHandler(e -> e.registry().register(
                            DIALOG_KEY,
                            Dialogs::whitelistDialog
                    )));

            StudentWhitelister.BOOTSTRAP_SUCCESSFUL = true;
        }
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        return PluginBootstrap.super.createPlugin(context);
    }
}
