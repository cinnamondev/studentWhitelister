package com.github.cinnamondev.studentWhitelister;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

public class Command {
    public static LiteralCommandNode<CommandSourceStack> command(StudentWhitelister p) {
        return Commands.literal("whitelister")
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("whitelister.reload"))
                        .executes(ctx -> {
                            p.reload().subscribe(ok -> {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("Successfully reloaded!")
                                );
                            }, ex -> ctx.getSource().getSender().sendMessage(
                                    Component.text(ex.getMessage())
                            ));

                            return 1;
                        })
                )
                .then(Commands.literal("dialog")
                        .requires(src -> src.getSender().hasPermission("whitelister.command.dialog"))
                        .requires(src -> src.getExecutor() != null)
                        .requires(src -> src.getExecutor() instanceof Player)
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(Component.text("login phase whitelister unsupported atm..."));
                            //ctx.getSource().getExecutor()
                            //        .showDialog(
                            //               RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG)
                            //                        .get(StudentWhitelisterBootstrap.DIALOG_KEY)
                            //        );
                            return 1;
                        })
                ).build();
    }
}
