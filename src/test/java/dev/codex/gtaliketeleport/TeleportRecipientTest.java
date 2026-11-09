package dev.codex.gtaliketeleport;

import java.util.UUID;

public final class TeleportRecipientTest {
    private static final UUID ISSUER_ID = UUID.fromString("3ce6a4c8-501d-457b-82ba-f8a55f2f47d1");

    public static void main(String[] args) {
        for (String command : new String[] {
                "/tp Receiver Issuer", "/tp Receiver ~ ~ ~", "/tp Receiver 10 64 20",
                "/teleport Receiver Issuer", "/minecraft:tp Receiver Issuer",
                "/tp \"Receiver\" \"Issuer\"", "/tp @a Issuer", "/tp @a ~ ~ ~",
                "/tp @p Issuer", "/tp @e[type=player] ~ ~ ~", "/tp @s[name=Receiver] ~ ~ ~",
                "/execute as Receiver run tp ~ ~ ~", "/execute as Receiver run tp @s Issuer",
                "/execute at Receiver run tp ~ ~ ~", "/execute positioned 1 2 3 run tp ~ ~ ~",
                "/execute in minecraft:the_nether run tp Receiver ~ ~ ~",
                "/execute in minecraft:the_nether run execute as Receiver run tp ~ ~ ~",
                "/say tp Receiver Issuer", "/tp", "/tp "
        }) {
            require(!targetsIssuer(command), "Must not preview another player's or unresolved teleport on the issuer: " + command);
        }
        for (String command : new String[] {
                "/tp Receiver", "/tp @p", "/tp 10 64 20", "/tp ~ ~ ~", "/tp ^ ^ ^5",
                "/tp Issuer Receiver", "/tp Issuer ~ ~ ~", "/tp issuer 1 2 3",
                "/tp @s Receiver", "/tp @s ~ ~ ~", "/tp \"Issuer\" Receiver",
                "/minecraft:teleport Issuer 1 2 3", "/teleport ~1 ~-2 ~3 90 0",
                "/execute in minecraft:the_nether run tp @s ~ ~ ~",
                "/minecraft:execute in minecraft:the_end run minecraft:tp 0 80 0",
                "/tp " + ISSUER_ID + " Receiver"
        }) {
            require(targetsIssuer(command), "Self teleports must preserve their local preview: " + command);
        }
        require(TeleportCommandMatcher.isTeleportCommand("/minecraft:execute in minecraft:the_end run tp 0 80 0"),
                "Namespaced execute must remain recognized");
        require(!TeleportCommandMatcher.isCoordinate("NaN") && !TeleportCommandMatcher.isCoordinate("Infinity"),
                "Non-finite coordinates cannot identify a self teleport");
        System.out.println("Teleport recipient command checks passed.");
    }

    private static boolean targetsIssuer(String command) {
        return TeleportCommandMatcher.targetsLocalPlayer(command, "Issuer", ISSUER_ID);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
