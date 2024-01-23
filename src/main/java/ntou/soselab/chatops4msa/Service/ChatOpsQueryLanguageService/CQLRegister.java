package ntou.soselab.chatops4msa.Service.ChatOpsQueryLanguageService;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import ntou.soselab.chatops4msa.Entity.CapabilityConfig.DevOpsTool.LowCode.DeclaredFunction;
import ntou.soselab.chatops4msa.Service.DiscordService.JDAService;
import ntou.soselab.chatops4msa.Service.LowCodeService.CapabilityConfigLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CQLRegister {
    private final JDA jda;
    private final CapabilityConfigLoader configLoader;

    @Autowired
    public CQLRegister(JDAService jdaService, CapabilityConfigLoader configLoader) {
        this.jda = jdaService.getJDA();
        this.configLoader = configLoader;

        // TODO: When executing for the first time, it's necessary to uncomment the following.
//        removeOriginalCommands();
//        upsertNewCommands();
//        checkCommandsStatusAndRestart(jdaService);
    }

    private void removeOriginalCommands() {
        System.out.println("[DEBUG] remove original commands");
        jda.retrieveCommands().queue(commands -> {
            for (Command command : commands) {
                jda.deleteCommandById(command.getId()).queue();
            }
        });
    }

    /**
     * command format:  /<<topCommand>> <<subCommandGroup>> <<subCommand>>
     * like             /set github issue
     */
    private void upsertNewCommands() {
        System.out.println("[DEBUG] upsert new commands...");
        for (Map.Entry<String, Map<String, List<SubcommandData>>> topEntry : generateCommandMap().entrySet()) {
            String topCommandName = topEntry.getKey();
            CommandCreateAction commandCreateAction = jda.upsertCommand(topCommandName, topCommandName);
            for (Map.Entry<String, List<SubcommandData>> midEntry : topEntry.getValue().entrySet()) {
                String subCommandGroupName = midEntry.getKey();
                String toolDescription = configLoader.getDevOpsToolObj(subCommandGroupName).getDescription();
                SubcommandGroupData subcommandGroupData = new SubcommandGroupData(subCommandGroupName, toolDescription);
                for (SubcommandData subcommandData : midEntry.getValue()) {
                    subcommandGroupData.addSubcommands(subcommandData);
                }
                commandCreateAction = commandCreateAction.addSubcommandGroups(subcommandGroupData);
            }
            commandCreateAction.queue();
        }

        jda.upsertCommand("check_all_subscription", "check all the subscriptions").queue();
        jda.upsertCommand("unsubscribe_all_capability", "unsubscribe all the capabilities").queue();
    }

    /**
     * only the service_name has selectable options
     */
    private Map<String, Map<String, List<SubcommandData>>> generateCommandMap() {
        Map<String, Map<String, List<SubcommandData>>> commandMap = new HashMap<>();
        for (DeclaredFunction declaredFunction : configLoader.getAllNonPrivateDeclaredFunctionMap().values()) {
            if (declaredFunction.isPrivate()) continue;

            String declaredFunctionName = declaredFunction.getName();
            String topCommandName = extractTopCommand(declaredFunctionName);
            String subCommandGroupName = extractSubCommandGroup(declaredFunctionName);
            String subCommandName = extractSubCommand(declaredFunctionName);
            String subCommandDescription = declaredFunction.getDescription();

            Map<String, List<SubcommandData>> subCommandGroupMap = commandMap.getOrDefault(topCommandName, new HashMap<>());
            List<SubcommandData> subCommandList = subCommandGroupMap.getOrDefault(subCommandGroupName, new ArrayList<>());

            SubcommandData subcommandData = new SubcommandData(subCommandName, subCommandDescription);
            for (Map.Entry<String, String> entry : declaredFunction.getParameterDescriptionMap().entrySet()) {
                if ("service_name".equals(entry.getKey())) subcommandData.addOptions(generateServiceOption());
                else subcommandData.addOption(OptionType.STRING, entry.getKey(), entry.getValue(), true);
            }
            subcommandData.addOptions(generateSubscribeOption());

            subCommandList.add(subcommandData);
            subCommandGroupMap.put(subCommandGroupName, subCommandList);
            commandMap.put(topCommandName, subCommandGroupMap);
        }
        return commandMap;
    }

    private String extractTopCommand(String functionName) {
        return functionName.split("-")[0];
    }

    private String extractSubCommandGroup(String functionName) {
        return functionName.split("-")[1];
    }

    private String extractSubCommand(String functionName) {
        return functionName.split("-")[2];
    }

    private OptionData generateServiceOption() {
        OptionData serviceOption = new OptionData(OptionType.STRING, "service_name", "service name", true);
        for (String serviceName : configLoader.getAllServiceNameList()) {
            serviceOption.addChoice(serviceName, serviceName);
        }
        return serviceOption.addChoice("all_service", "all_service");
    }

    /**
     * e.g. Execute regularly every day at 9 AM.    [0 0 9 * * ?]
     * e.g. Execute regularly every Monday at 9 AM. [0 0 9 ? * MON]
     * e.g. Execute regularly every minute.         [0 * * * * ?]
     */
    private OptionData generateSubscribeOption() {
        return new OptionData(OptionType.STRING, "subscribe", "cron expression (e.g. every day at 9 AM. [0 0 9 * * ?])", false);
    }

    private void checkCommandsStatusAndRestart(JDAService jdaService) {

        try {
            jda.awaitReady();
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        String warningMessage = "[WARNING] Failed To Register ChatOps Query Language (Restarting...)";
        String successMessage = "All ChatOps Query Language has been registered";

        jda.retrieveCommands().queue(commands -> {
            System.out.println();

            // add this comment to prevent being BANNED by Discord due to excessive frequent calls
//            int commandNumber = configLoader.getAllNonPrivateDeclaredFunctionMap().size();
//            if (commands.size() < commandNumber) {
            if (commands.isEmpty()) {
                jdaService.sendChatOpsChannelWarningMessage(warningMessage);
                System.out.println(warningMessage);
                System.out.println();
                upsertNewCommands();
                checkCommandsStatusAndRestart(jdaService);
                return;
            }

            jdaService.sendChatOpsChannelInfoMessage("[INFO] " + successMessage);
            System.out.println("[DEBUG] " + successMessage);
            System.out.println();

            System.out.println("[DEBUG] all the capabilities:");
            for (Command command : commands) {
                for (Command.SubcommandGroup subcommandGroup : command.getSubcommandGroups()) {
                    for (Command.Subcommand subCommand : subcommandGroup.getSubcommands()) {
                        System.out.println("/" + subCommand.getFullCommandName());
                    }
                }
            }

            System.out.println();
        });
    }
}
