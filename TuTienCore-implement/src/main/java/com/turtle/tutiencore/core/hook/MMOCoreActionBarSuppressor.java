package com.turtle.tutiencore.core.hook;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.turtle.tutiencore.core.manager.TuLuyenManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MMOCoreActionBarSuppressor {

    private final Plugin plugin;
    private final TuLuyenManager tuLuyenManager;
    private final Map<UUID, Long> actionBarBypassUntil = new HashMap<>();
    private PacketListener listener;

    public MMOCoreActionBarSuppressor(Plugin plugin, TuLuyenManager tuLuyenManager) {
        this.plugin = plugin;
        this.tuLuyenManager = tuLuyenManager;
    }

    public void register() {
        if (listener != null) {
            return;
        }

        listener = new PacketAdapter(plugin, PacketType.Play.Server.SET_ACTION_BAR_TEXT, PacketType.Play.Server.SYSTEM_CHAT, PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null || !tuLuyenManager.isTuLuyen(player)) {
                    return;
                }

                if (!isActionBarPacket(event)) {
                    return;
                }

                if (consumeBypass(player.getUniqueId())) {
                    return;
                }

                event.setCancelled(true);
            }
        };

        ProtocolLibrary.getProtocolManager().addPacketListener(listener);
        plugin.getLogger().info("MMOCore action bar suppressor registered.");
    }

    public void allowNextActionBar(Player player) {
        actionBarBypassUntil.put(player.getUniqueId(), System.currentTimeMillis() + 250L);
    }

    public void unregister() {
        if (listener == null) {
            return;
        }

        ProtocolLibrary.getProtocolManager().removePacketListener(listener);
        listener = null;
        actionBarBypassUntil.clear();
    }

    private boolean isActionBarPacket(PacketEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SET_ACTION_BAR_TEXT) {
            return true;
        }

        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT) {
            try {
                return Boolean.TRUE.equals(event.getPacket().getBooleans().readSafely(0));
            } catch (Exception ignored) {
                return false;
            }
        }

        try {
            return event.getPacket().getChatTypes().readSafely(0) == EnumWrappers.ChatType.GAME_INFO;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String readText(PacketEvent event) {
        try {
            WrappedChatComponent component = event.getPacket().getChatComponents().readSafely(0);
            return component != null ? component.getJson() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean consumeBypass(UUID uuid) {
        Long expiresAt = actionBarBypassUntil.remove(uuid);
        return expiresAt != null && expiresAt >= System.currentTimeMillis();
    }
}
