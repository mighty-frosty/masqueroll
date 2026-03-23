package vtm.masqueroll;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import vtm.masqueroll.dice.DiceDisplayConfig;
import vtm.masqueroll.dice.PendingRoll;
import vtm.masqueroll.dice.RollImageRenderer;
import vtm.masqueroll.dice.StatusBannerRenderer;
import vtm.masqueroll.commands.CharacterCommand;
import vtm.masqueroll.commands.Command;
import vtm.masqueroll.commands.CommandContext;
import vtm.masqueroll.commands.DamageCommand;
import vtm.masqueroll.commands.DeleteCharacterCommand;
import vtm.masqueroll.commands.HealCommand;
import vtm.masqueroll.commands.HelpCommand;
import vtm.masqueroll.commands.MacroCommand;
import vtm.masqueroll.commands.MystatsCommand;
import vtm.masqueroll.commands.RemoveMacroCommand;
import vtm.masqueroll.commands.RestoreCommand;
import vtm.masqueroll.commands.RollCommand;
import vtm.masqueroll.commands.RouseCommand;
import vtm.masqueroll.commands.SetCommand;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DiceCommandListener extends ListenerAdapter {

    private final CommandContext context;
    private final List<Command> commands;

    public DiceCommandListener(DiceDisplayConfig displayConfig, RollImageRenderer imageRenderer) {
        CharacterSheetService characterSheetService = new CharacterSheetService();
        Map<String, PendingRoll> pendingRolls = new ConcurrentHashMap<>();
        StatusBannerRenderer statusBannerRenderer = new StatusBannerRenderer(java.nio.file.Path.of("src", "fonts"));

        this.context = new CommandContext(characterSheetService, displayConfig, imageRenderer, statusBannerRenderer);
        this.commands = List.of(
            new HelpCommand(context),
            new RollCommand(context, pendingRolls),
            new CharacterCommand(context),
            new DeleteCharacterCommand(context),
            new SetCommand(context),
            new MacroCommand(context),
            new RemoveMacroCommand(context),
            new DamageCommand(context),
            new HealCommand(context),
            new RestoreCommand(context),
            new RouseCommand(context),
            new MystatsCommand(context)
        );
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        if (handleSheetTemplateReply(event)) {
            return;
        }

        String content = event.getMessage().getContentRaw().trim();
        for (Command command : commands) {
            if (command.matchesMessage(content)) {
                command.handleMessage(event, content);
                return;
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        for (Command command : commands) {
            if (command.matchesSlash(event.getName())) {
                command.handleSlash(event);
                return;
            }
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        for (Command command : commands) {
            if (command.handleButton(event)) {
                return;
            }
        }
    }

    private boolean handleSheetTemplateReply(MessageReceivedEvent event) {
        var referenced = event.getMessage().getReferencedMessage();
        if (referenced == null || !referenced.getAuthor().isBot()) {
            return false;
        }

        if (!referenced.getContentRaw().contains(context.sheetTemplateMarker())) {
            return false;
        }

        context.characterSheetService().replaceSheet(
            event.getGuild(),
            event.getAuthor().getId(),
            event.getMessage().getContentRaw(),
            sheet -> event.getChannel().sendMessage("Updated your full character sheet.").queue(),
            error -> event.getChannel().sendMessage(error).queue()
        );
        return true;
    }
}
