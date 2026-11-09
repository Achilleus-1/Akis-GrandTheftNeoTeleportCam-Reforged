package dev.codex.gtaliketeleport;

import java.util.Locale;
import java.util.UUID;

final class TeleportCommandMatcher {
    private TeleportCommandMatcher() {
    }

    static boolean isTeleportCommand(String command) {
        return TeleportCommandMatcher.getArgumentString(command) != null;
    }

    /** Only preview commands whose moving player can be identified on this client. */
    static boolean targetsLocalPlayer(String command, String playerName, UUID playerId) {
        String normalized = command.stripLeading();
        if (normalized.startsWith("/")) normalized = normalized.substring(1).stripLeading();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("execute ") || lower.startsWith("minecraft:execute ")) {
            int runIndex = lower.indexOf(" run ");
            if (runIndex < 0) return false;
            String[] context = normalized.substring(0, runIndex).strip().split("\\s+");
            // Other execute contexts can change the executor, selector results,
            // or relative-coordinate origin. Let the actual recipient handle them.
            if (context.length != 3 || !context[1].equalsIgnoreCase("in")) return false;
            return targetsLocalPlayer(normalized.substring(runIndex + 5), playerName, playerId);
        }
        String arguments = getArgumentString(normalized);
        if (arguments == null || arguments.isBlank()) return false;
        String[] args = arguments.strip().split("\\s+");
        if (args.length == 1) return true;
        if (args.length >= 3 && isCoordinate(args[0]) && isCoordinate(args[1]) && isCoordinate(args[2])) {
            return true;
        }
        String target = args[0];
        if (target.length() >= 2 && target.startsWith("\"") && target.endsWith("\"")) {
            target = target.substring(1, target.length() - 1);
        }
        return target.equals("@s") || target.equalsIgnoreCase(playerName) || target.equalsIgnoreCase(playerId.toString());
    }

    static boolean isCoordinate(String token) {
        if (token.isEmpty()) return false;
        if (token.charAt(0) == '~' || token.charAt(0) == '^') {
            if (token.length() == 1) return true;
            token = token.substring(1);
        }
        try {
            return Double.isFinite(Double.parseDouble(token));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static String getArgumentString(String command) {
        int runIndex;
        String normalized = command.stripLeading();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).stripLeading();
        }
        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        for (String name : new String[]{"minecraft:teleport", "minecraft:tp", "teleport", "tp"}) {
            if (lowerCase.equals(name)) {
                return "";
            }
            if (!lowerCase.startsWith(name + " ")) continue;
            return normalized.substring(name.length()).stripLeading();
        }
        if ((lowerCase.startsWith("execute ") || lowerCase.startsWith("minecraft:execute ")) && (runIndex = lowerCase.indexOf(" run ")) >= 0) {
            return TeleportCommandMatcher.getArgumentString(normalized.substring(runIndex + 5).stripLeading());
        }
        return null;
    }
}
