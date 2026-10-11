package dev.codex.gtaliketeleport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import dev.codex.gtaliketeleport.DimensionIds;
import dev.codex.gtaliketeleport.GtaLikeTeleportClientNetworking;
import dev.codex.gtaliketeleport.GtaLikeTeleportConfig;
import dev.codex.gtaliketeleport.GtaLikeTeleportConfigScreen;
import dev.codex.gtaliketeleport.GtaLikeTeleportNetworkPayloads;
import dev.codex.gtaliketeleport.TeleportCommandMatcher;
import dev.codex.gtaliketeleport.TeleportTransitionController;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;

public final class GtaLikeTeleportClient {
    private static final String[] COMMAND_ALIASES = new String[]{"grandtp", "gtp"};
    private static final String USAGE_MESSAGE = "Usage: /gtp or /grandtp on|off|status|player_freeze <on|off|status>";
    private static boolean bypassNextCommand;
    private static boolean bypassNextPacket;
    private static boolean bypassNextJourneyMapTeleport;
    private static boolean openConfigScreenNextTick;

    static void initializeClient() {
        GtaLikeTeleportConfig.load();
        GtaLikeTeleportNetworkPayloads.register();
        GtaLikeTeleportClientNetworking.registerReceivers();
    }

    static void tick(Minecraft client) {
        if (openConfigScreenNextTick) {
            openConfigScreenNextTick = false;
            if (client != null) {
                client.setScreen((Screen)new GtaLikeTeleportConfigScreen(null));
            }
        }
        TeleportTransitionController.tick(client);
    }

    static void registerClientCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String commandName : COMMAND_ALIASES) {
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)commandName).executes(context -> GtaLikeTeleportClient.executeLocalCommand(commandName))).then(Commands.literal((String)"on").executes(context -> GtaLikeTeleportClient.executeLocalCommand(commandName + " on")))).then(Commands.literal((String)"off").executes(context -> GtaLikeTeleportClient.executeLocalCommand(commandName + " off")))).then(Commands.literal((String)"status").executes(context -> GtaLikeTeleportClient.executeLocalCommand(commandName + " status")))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"player_freeze").executes(context -> GtaLikeTeleportClient.executeLocalCommand(commandName + " player_freeze status"))).then(Commands.literal((String)"on").executes(context -> GtaLikeTeleportClient.executeLocalCommand(commandName + " player_freeze on")))).then(Commands.literal((String)"off").executes(context -> GtaLikeTeleportClient.executeLocalCommand(commandName + " player_freeze off")))).then(Commands.literal((String)"status").executes(context -> GtaLikeTeleportClient.executeLocalCommand(commandName + " player_freeze status"))))).then(Commands.argument((String)"argument", (ArgumentType)StringArgumentType.greedyString()).executes(context -> GtaLikeTeleportClient.executeLocalCommand(commandName + " " + StringArgumentType.getString((CommandContext)context, (String)"argument")))));
        }
    }

    private static int executeLocalCommand(String command) {
        GtaLikeTeleportClient.handleGtaTeleportCommand(Minecraft.getInstance(), command);
        return 1;
    }

    public static boolean interceptOutgoingCommand(String command) {
        if (bypassNextCommand) {
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (GtaLikeTeleportClient.handleGtaTeleportCommand(client, command)) {
            return false;
        }
        if (!GtaLikeTeleportConfig.isEffectEnabled()) {
            return true;
        }
        if (!TeleportCommandMatcher.isTeleportCommand(command) || client.player == null || client.getConnection() == null) {
            return true;
        }
        if (!GtaLikeTeleportClient.canExecuteServerCommand(client, command)) {
            return true;
        }
        if (TeleportTransitionController.isRunning()) {
            return true;
        }
        TeleportTransitionController.start(client, command);
        return false;
    }

    public static boolean interceptOutgoingPacket(Connection connection, Packet<?> packet, PacketSendListener listener) {
        if (bypassNextPacket) {
            return true;
        }
        PacketTeleportTarget teleportTarget = GtaLikeTeleportClient.getTeleportPacketTarget(packet);
        if (teleportTarget == null) {
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (!GtaLikeTeleportConfig.isEffectEnabled() || client.player == null || client.level == null || client.getConnection() == null) {
            return true;
        }
        if (TeleportTransitionController.isRunning()) {
            return true;
        }
        TeleportTransitionController.start(client, teleportTarget.targetFeet(), teleportTarget.targetDimensionId(), () -> GtaLikeTeleportClient.sendDeferredPacket(connection, packet, listener), !teleportTarget.keepMenuOpen());
        return false;
    }

    public static boolean interceptJourneyMapTeleport(Vec3 targetFeet, Runnable action) {
        return GtaLikeTeleportClient.interceptJourneyMapTeleport(targetFeet, null, action);
    }

    public static boolean interceptJourneyMapTeleport(Vec3 targetFeet, String targetDimensionId, Runnable action) {
        if (bypassNextJourneyMapTeleport) {
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (!GtaLikeTeleportConfig.isEffectEnabled() || client.player == null || client.level == null || client.getConnection() == null) {
            return true;
        }
        if (TeleportTransitionController.isRunning()) {
            return true;
        }
        TeleportTransitionController.start(client, targetFeet, targetDimensionId, () -> GtaLikeTeleportClient.sendDeferredJourneyMapTeleport(action));
        return false;
    }

    static void handleServerTeleportRequest(GtaLikeTeleportNetworkPayloads.StartServerTeleportPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (!GtaLikeTeleportClient.shouldPlayServerTeleportTransition(client, payload.source())) {
            GtaLikeTeleportClientNetworking.sendServerTeleportAck(payload.requestId());
            return;
        }
        TeleportTransitionController.start(client, GtaLikeTeleportClientNetworking.targetFeet(payload), GtaLikeTeleportClientNetworking.targetDimensionId(payload), () -> GtaLikeTeleportClientNetworking.sendServerTeleportAck(payload.requestId()), payload.source());
    }

    private static boolean shouldPlayServerTeleportTransition(Minecraft client, int source) {
        if (!GtaLikeTeleportConfig.isEffectEnabled() || client.player == null || client.level == null || client.getConnection() == null) {
            return false;
        }
        if (TeleportTransitionController.isRunning()) {
            return false;
        }
        if (source == 2) {
            return GtaLikeTeleportConfig.isWarpPlateTransitionsEnabled();
        }
        return GtaLikeTeleportConfig.isExternalTeleportTransitionsEnabled();
    }

    static void sendDeferredCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            return;
        }
        GtaLikeTeleportClientNetworking.sendBypassNextServerTeleport();
        bypassNextCommand = true;
        try {
            client.getConnection().sendCommand(command);
        }
        finally {
            bypassNextCommand = false;
        }
    }

    private static void sendDeferredPacket(Connection connection, Packet<?> packet, PacketSendListener listener) {
        GtaLikeTeleportClientNetworking.sendBypassNextServerTeleport();
        bypassNextPacket = true;
        try {
            if (listener == null) {
                connection.send(packet);
            } else {
                connection.send(packet, listener);
            }
        }
        finally {
            bypassNextPacket = false;
        }
    }

    private static void sendDeferredJourneyMapTeleport(Runnable action) {
        GtaLikeTeleportClientNetworking.sendBypassNextServerTeleport();
        bypassNextJourneyMapTeleport = true;
        try {
            action.run();
        }
        finally {
            bypassNextJourneyMapTeleport = false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static PacketTeleportTarget getTeleportPacketTarget(Packet<?> packet) {
        if (!(packet instanceof ServerboundCustomPayloadPacket customPayloadPacket)) {
            return null;
        }

        CustomPacketPayload payload = customPayloadPacket.payload();
        ResourceLocation id = payload.type().id();
        PacketTeleportTarget journeyMapTarget = getJourneyMapTeleportTarget(payload, id);
        if (journeyMapTarget != null) {
            return journeyMapTarget;
        }

        return getWaystonesTeleportTarget(payload, id);
    }

    private static PacketTeleportTarget getJourneyMapTeleportTarget(CustomPacketPayload payload, ResourceLocation id) {
        if (!id.getNamespace().equals("journeymap") || !id.getPath().equals("teleport_req")) {
            return null;
        }

        try {
            Vec3 targetFeet = new Vec3(
                    readDouble(payload, "getX"),
                    readDouble(payload, "getY"),
                    readDouble(payload, "getZ")
            );
            return new PacketTeleportTarget(targetFeet, readOptionalDimensionId(payload, "getDimension"), false);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassCastException ignored) {
            return null;
        }
    }

    private static PacketTeleportTarget getWaystonesTeleportTarget(CustomPacketPayload payload, ResourceLocation id) {
        if (!id.getNamespace().equals("waystones")) {
            return null;
        }

        if (id.getPath().equals("select_waystone")) {
            WaystoneTarget target = getWaystonesSelectedTarget(payload);
            return target == null ? null : new PacketTeleportTarget(target.targetFeet(), target.targetDimensionId(), true);
        }

        if (id.getPath().equals("inventory_button")) {
            WaystoneTarget target = getWaystonesInventoryButtonTarget();
            return target == null ? null : new PacketTeleportTarget(target.targetFeet(), target.targetDimensionId(), false);
        }

        return null;
    }

    private static WaystoneTarget getWaystonesSelectedTarget(CustomPacketPayload payload) {
        try {
            UUID waystoneUid = readUuid(payload, "waystoneUid");
            Minecraft client = Minecraft.getInstance();
            Object menu = client.player == null ? null : client.player.containerMenu;
            WaystoneTarget menuTarget = findWaystoneTargetInMenu(menu, waystoneUid);
            return menuTarget != null ? menuTarget : findWaystoneTargetInStore(waystoneUid);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException | ClassCastException ignored) {
            return null;
        }
    }

    private static WaystoneTarget getWaystonesInventoryButtonTarget() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }
        try {
            Optional optional;
            Class<?> managerClass = Class.forName("net.blay09.mods.waystones.core.PlayerWaystoneManager");
            Method method = managerClass.getMethod("getInventoryButtonTarget", Player.class);
            Object result = method.invoke(null, client.player);
            if (!(result instanceof Optional) || (optional = (Optional)result).isEmpty()) {
                return null;
            }
            return GtaLikeTeleportClient.getWaystoneTarget(optional.get());
        }
        catch (ClassCastException | ClassNotFoundException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static WaystoneTarget findWaystoneTargetInMenu(Object menu, UUID waystoneUid) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (menu == null) {
            return null;
        }
        Method method = menu.getClass().getMethod("getWaystones", new Class[0]);
        Object result = method.invoke(menu, new Object[0]);
        if (!(result instanceof Collection)) {
            return null;
        }
        Collection waystones = (Collection)result;
        for (Object waystone : waystones) {
            if (!waystoneUid.equals(GtaLikeTeleportClient.readUuid(waystone, "getWaystoneUid"))) continue;
            return GtaLikeTeleportClient.getWaystoneTarget(waystone);
        }
        return null;
    }

    private static WaystoneTarget findWaystoneTargetInStore(UUID waystoneUid) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Optional optional;
        Class<?> clientClass = Class.forName("net.blay09.mods.waystones.client.WaystonesClient");
        Object store = clientClass.getMethod("getWaystonesStore", new Class[0]).invoke(null, new Object[0]);
        Object result = store.getClass().getMethod("getWaystoneById", UUID.class).invoke(store, waystoneUid);
        if (!(result instanceof Optional) || (optional = (Optional)result).isEmpty()) {
            return null;
        }
        return GtaLikeTeleportClient.getWaystoneTarget(optional.get());
    }

    private static WaystoneTarget getWaystoneTarget(Object waystone) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object result = waystone.getClass().getMethod("getPos", new Class[0]).invoke(waystone, new Object[0]);
        if (!(result instanceof BlockPos)) {
            return null;
        }
        BlockPos pos = (BlockPos)result;
        Vec3 targetFeet = new Vec3((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5);
        return new WaystoneTarget(targetFeet, GtaLikeTeleportClient.readOptionalDimensionId(waystone, "getDimension"));
    }

    private static UUID readUuid(Object target, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Method method = target.getClass().getMethod(methodName);
            return (UUID) method.invoke(target);
        } catch (NoSuchMethodException noGetter) {
            try {
                Field field = target.getClass().getDeclaredField(methodName);
                field.setAccessible(true);
                return (UUID) field.get(target);
            } catch (NoSuchFieldException noField) {
                throw noGetter;
            }
        }
    }
    private static double readDouble(Object target, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = target.getClass().getMethod(methodName);
        return ((Number) method.invoke(target)).doubleValue();
    }

    private static String readOptionalDimensionId(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName, new Class[0]);
            Object result = method.invoke(target, new Object[0]);
            if (result instanceof ResourceKey) {
                ResourceKey key = (ResourceKey)result;
                return DimensionIds.fromResourceKey(key);
            }
            return DimensionIds.normalize(result == null ? null : result.toString());
        }
        catch (ClassCastException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static boolean canExecuteServerCommand(Minecraft client, String command) {
        ClientPacketListener networkHandler = client.getConnection();
        if (networkHandler == null) {
            return false;
        }
        String normalized = GtaLikeTeleportClient.normalizeCommand(command);
        if (normalized.isEmpty()) {
            return false;
        }
        ParseResults parseResults = networkHandler.getCommands().parse(normalized, networkHandler.getSuggestionsProvider());
        return !parseResults.getReader().canRead() && GtaLikeTeleportClient.hasExecutableCommand(parseResults.getContext());
    }

    private static boolean hasExecutableCommand(CommandContextBuilder<?> context) {
        for (CommandContextBuilder current = context; current != null; current = current.getChild()) {
            if (current.getCommand() == null) continue;
            return true;
        }
        return false;
    }

    private static String normalizeCommand(String command) {
        String normalized = command.strip();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).stripLeading();
        }
        return normalized;
    }

    private static String getLocalCommandName(String normalized) {
        int end;
        for (end = 0; end < normalized.length() && !Character.isWhitespace(normalized.charAt(end)); ++end) {
        }
        String commandName = normalized.substring(0, end).toLowerCase(Locale.ROOT);
        for (String alias : COMMAND_ALIASES) {
            if (!commandName.equals(alias)) continue;
            return normalized.substring(0, end);
        }
        return null;
    }

    private static boolean handleGtaTeleportCommand(Minecraft client, String command) {
        String commandName;
        String normalized = command.stripLeading();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).stripLeading();
        }
        if ((commandName = GtaLikeTeleportClient.getLocalCommandName(normalized)) == null) {
            return false;
        }
        String argument = normalized.length() == commandName.length() ? "" : normalized.substring(commandName.length()).strip();
        String lowerArgument = argument.toLowerCase(Locale.ROOT);
        if (lowerArgument.equals("on")) {
            boolean saved = GtaLikeTeleportConfig.setEffectEnabled(true);
            GtaLikeTeleportClient.sendCommandFeedback(client, true, saved);
            return true;
        }
        if (lowerArgument.equals("off")) {
            boolean saved = GtaLikeTeleportConfig.setEffectEnabled(false);
            GtaLikeTeleportClient.sendCommandFeedback(client, false, saved);
            return true;
        }
        if (lowerArgument.isEmpty()) {
            openConfigScreenNextTick = true;
            return true;
        }
        if (lowerArgument.equals("status")) {
            GtaLikeTeleportClient.sendFeedback(client, GtaLikeTeleportClient.createStateFeedback(GtaLikeTeleportConfig.isEffectEnabled(), true, ChatFormatting.GRAY));
            return true;
        }
        if (lowerArgument.equals("player_freeze") || lowerArgument.equals("player_freeze status")) {
            GtaLikeTeleportClient.sendFeedback(client, GtaLikeTeleportClient.createPlayerFreezeStateFeedback(GtaLikeTeleportConfig.isPlayerFreezeEnabled(), true, ChatFormatting.GRAY));
            return true;
        }
        if (lowerArgument.equals("player_freeze on")) {
            boolean saved = GtaLikeTeleportConfig.setPlayerFreezeEnabled(true);
            GtaLikeTeleportClient.sendFeedback(client, GtaLikeTeleportClient.createPlayerFreezeStateFeedback(true, saved, saved ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            return true;
        }
        if (lowerArgument.equals("player_freeze off")) {
            boolean saved = GtaLikeTeleportConfig.setPlayerFreezeEnabled(false);
            GtaLikeTeleportClient.sendFeedback(client, GtaLikeTeleportClient.createPlayerFreezeStateFeedback(false, saved, saved ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            return true;
        }
        GtaLikeTeleportClient.sendFeedback(client, (Component)Component.literal((String)USAGE_MESSAGE).withStyle(ChatFormatting.RED));
        return true;
    }

    private static void sendCommandFeedback(Minecraft client, boolean enabled, boolean saved) {
        GtaLikeTeleportClient.sendFeedback(client, GtaLikeTeleportClient.createStateFeedback(enabled, saved, saved ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    private static Component createStateFeedback(boolean enabled, boolean saved, ChatFormatting formatting) {
        String state = enabled ? "ON" : "OFF";
        String message = "Aki's Grand Theft Neo Teleport Cam Reforged:" + state + (saved ? "" : " (save failed)");
        return Component.literal((String)message).withStyle(formatting);
    }

    private static Component createPlayerFreezeStateFeedback(boolean enabled, boolean saved, ChatFormatting formatting) {
        String state = enabled ? "ON" : "OFF";
        String message = "Aki's Grand Theft Neo Teleport Cam Reforged player_freeze:" + state + (saved ? "" : " (save failed)");
        return Component.literal((String)message).withStyle(formatting);
    }

    private static void sendFeedback(Minecraft client, Component message) {
        if (client.player != null) {
            client.player.sendSystemMessage(message);
        }
    }

    private record PacketTeleportTarget(Vec3 targetFeet, String targetDimensionId, boolean keepMenuOpen) {
    }

    private record WaystoneTarget(Vec3 targetFeet, String targetDimensionId) {
    }
}
