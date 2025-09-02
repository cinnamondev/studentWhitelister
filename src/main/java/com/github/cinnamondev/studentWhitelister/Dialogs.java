package com.github.cinnamondev.studentWhitelister;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class Dialogs {
    public static final Key GENERIC_CANCEL_BUTTON_KEY = Key.key("subot:user_input/cancel_button");
    public static final ActionButton GENERIC_CANCEL_BUTTON = ActionButton.create(
            Component.text("Cancel").color(NamedTextColor.DARK_RED),
            null, 200,
            DialogAction.customClick(GENERIC_CANCEL_BUTTON_KEY, null)
    );

    public static final Key REGISTER_BUTTON_KEY = Key.key("subot:user_input/register_button");
    ///  whitelist dialog (intended to be registered at bootstrap)
    public static DialogRegistryEntry.Builder whitelistDialog(DialogRegistryEntry.Builder builder) {
        return builder
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("Submit registration").color(NamedTextColor.GREEN),
                                null, 200,
                                DialogAction.customClick(REGISTER_BUTTON_KEY, null)
                        ),
                        GENERIC_CANCEL_BUTTON
                ))
                .base(DialogBase.builder(Component.text("Get whitelisted!"))
                        .externalTitle(Component.text("Get whitelisted!"))
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(
                                DialogBody.item(ItemStack.of(Material.COMMAND_BLOCK)).showTooltip(false).showDecorations(false).build(),
                                DialogBody.plainMessage(StudentWhitelister.DIALOG_MESSAGE, 320)
                        ))
                        .inputs(List.of(
                                DialogInput.text("identifier", 128, Component.text("Student Email / ID"), true, "", 254, null),
                                DialogInput.text("discord", 128, Component.text("Discord Username"), true, "", 32, null)
                        ))
                        .pause(true)
                        .canCloseWithEscape(false)
                        .build()
                );
    }

    public static final Key RETRY_BUTTON_KEY = Key.key("subot:user_input/try_again");
    /// build a dialog on call detailed with the error information
    public static Dialog userErrorDialog(Throwable t, boolean allowRetry) {
        // https://stackoverflow.com/a/77159261 hatehatehatehatehatehatehatehatehate
        Component message;
        if (t instanceof ExecutionException ex) {
            t = ex.getCause();
        }

        if (t instanceof Exceptions.IdentifierValidationException) {
            message = Component.text("Identifier invalid, should be either a valid e-mail address or student ID number.");
        } else if (t instanceof Exceptions.DiscordValidationException) {
            message = Component.text("Discord user not found, please make sure you are providing your username and not nickname/display name.");
        } else {
            message = t.getMessage() != null
                    ? Component.text("Unknown error: (" +         t.getClass().toGenericString()
                     +")  " +  t.getMessage())
                    : Component.text("Unknown error, no message to provide.");
        }

        return Dialog.create(b -> b.empty()
                .type(allowRetry
                        ? DialogType.confirmation(ActionButton.create(
                                Component.text("Try again"),
                                null,
                                200,
                                DialogAction.customClick(RETRY_BUTTON_KEY, null)
                        ), GENERIC_CANCEL_BUTTON)
                        : DialogType.notice(GENERIC_CANCEL_BUTTON))
                .base(DialogBase.create(
                        Component.text("Error!"),
                        Component.text("Error!"),
                        false, false, DialogBase.DialogAfterAction.CLOSE,
                        List.of(
                                DialogBody.item(ItemStack.of(Material.BARRIER,1))
                                        .showDecorations(false)
                                        .showTooltip(false)
                                        .build(),
                                DialogBody.plainMessage(message, 300)
                        ),
                        Collections.emptyList()
                )));
    }
}
