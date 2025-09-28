package com.github.cinnamondev.studentWhitelister;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.entity.Player;

import java.util.List;

public class Command {
    private final StudentWhitelister p;
    public Command(StudentWhitelister p) {
        this.p = p;
    }

    private int reload(CommandContext<CommandSourceStack> context) { return p.reload(); }
    public LiteralCommandNode<CommandSourceStack> COMMAND = Commands.literal("whitelister")
            .then(Commands.literal("reload")
                    .requires(src -> src.getSender().hasPermission("whitelister.reload"))
                    .executes(this::reload)
            )
            .then(Commands.literal("dialog")
                    .requires(src -> src.getSender().hasPermission("whitelister.command.dialog"))
                    .requires(src-> src.getExecutor() != null)
                    .requires(src -> src.getExecutor() instanceof Player)
                    .executes(ctx -> {
                        ctx.getSource().getExecutor()
                                .showDialog(
                                        RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG)
                                                .get(StudentWhitelisterBootstrap.DIALOG_KEY)
                                );
                        return 1;
                    })
            ).build();
}
