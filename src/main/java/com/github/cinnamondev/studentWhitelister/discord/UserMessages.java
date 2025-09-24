package com.github.cinnamondev.studentWhitelister.discord;

import com.github.cinnamondev.studentWhitelister.StudentWhitelister;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import org.w3c.dom.Text;

import java.util.Collections;

public class UserMessages {
    public static final String OTHER_KEY = "other_reason";
    public static final String OTHER_REJECTION_REASON = "Other rejection reason";
    public static final String EMAIL_KEY = "invalid_email";
    public static final String NON_MANCUNIAN_EMAIL = "Not a UoM email address";
    public static final String SU_KEY = "not_registered";
    public static final String NOT_IN_STUDENT_UNION = "Not registered as member with SU";

    public static String keyToReason(String key) {
        return switch (key) {
            case SU_KEY -> NOT_IN_STUDENT_UNION;
            case EMAIL_KEY -> NON_MANCUNIAN_EMAIL;
            //case OTHER_KEY -> OTHER_REJECTION_REASON;
            default -> OTHER_REJECTION_REASON;
        };
    }
    public static MessageCreateSpec rejectPrivateChannelResponse(String key) {
         TextDisplay content = switch (key) {
             case SU_KEY -> TextDisplay.of("""
                     You are not registered as a member with us on the SU!
                     Please go to our SU page and register as a member. If you are a member, please
                     double check that the details you have given us are correct. """);
            case EMAIL_KEY -> TextDisplay.of("You did not provide an email ending in @manchester.ac.uk :( If you are an associate member, seek help...");
            default -> TextDisplay.of("Unknown rejection reason! Please ask in the discord...");
        };

        return MessageCreateSpec.builder().build()
                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                .withComponents(
                        content,
                        ActionRow.of(Button.link(StudentWhitelister.STUDENT_UNION_LINK, "Student Union Page"), Button.primary("getWhitelistedModal", "Try again"))
                );
    }

    public static MessageCreateSpec acceptPrivateChannelResponse(boolean isJava) {
        String ending = isJava
                ? "You can join the server at: ```" + Bot.SERVER_HOSTNAME + "```"
                : "You can connect with the server details ```uomc.net``` on port ```" + Bot.SERVER_BEDROCK_PORT +"```.";

        return MessageCreateSpec.builder()
                .content("You have been whitelisted! :) \n" + ending
                        + "\n If you need more help, check out " + Bot.INFO_CHANNEL_STRING + ", or ask in " + Bot.HELP_CHANNEL_STRING +".")
                .build();
    }


}
