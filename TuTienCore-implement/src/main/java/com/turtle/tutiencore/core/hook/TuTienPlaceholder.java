package com.turtle.tutiencore.core.hook;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.manager.RealmManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TuTienPlaceholder extends PlaceholderExpansion {

    private final RealmManager realmManager;
    private final JavaPlugin plugin;

    public TuTienPlaceholder(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.realmManager = realmManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tutien";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Turtle";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; 
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        double tuvi = TuTien.getApi().getTuVi(player.getUniqueId());

        // ==========================================
        // Tu Vi Placeholders
        // ==========================================
        if (params.equalsIgnoreCase("tuvi")) {
            return String.valueOf(tuvi);
        }
        else if (params.equalsIgnoreCase("tuvi_int")) {
            return String.valueOf((long) tuvi);
        }
        else if (params.equalsIgnoreCase("tuvi_formatted")) {
            return String.format("%,.0f", tuvi);
        }
        else if (params.equalsIgnoreCase("tuvi_compact")) {
            return formatCompact(tuvi);
        }

        // ==========================================
        // Thoi gian Tu Luyen
        // ==========================================
        else if (params.equalsIgnoreCase("tuluyen_active")) {
            return TuTien.getApi().isTuLuyen(player.getUniqueId()) ? "true" : "false";
        }
        else if (params.equalsIgnoreCase("tuluyen_total_seconds")
                || params.equalsIgnoreCase("tuluyen_time_seconds")) {
            return String.valueOf(TuTien.getApi().getTuLuyenTotalSeconds(player.getUniqueId()));
        }
        else if (params.equalsIgnoreCase("tuluyen_total_time")
                || params.equalsIgnoreCase("tuluyen_time")) {
            return formatDurationHms(TuTien.getApi().getTuLuyenTotalSeconds(player.getUniqueId()));
        }
        else if (params.equalsIgnoreCase("tuluyen_total_compact")
                || params.equalsIgnoreCase("tuluyen_time_compact")) {
            return formatDurationCompact(TuTien.getApi().getTuLuyenTotalSeconds(player.getUniqueId()));
        }
        else if (params.equalsIgnoreCase("tuluyen_total_format")
                || params.equalsIgnoreCase("tuluyen_time_format")
                || params.equalsIgnoreCase("tuluyen_total_formatted")
                || params.equalsIgnoreCase("tuluyen_time_formatted")) {
            return formatConfiguredDuration(plugin.getConfig(), TuTien.getApi().getTuLuyenTotalSeconds(player.getUniqueId()));
        }
        else if (params.equalsIgnoreCase("tuluyen_session_seconds")) {
            return String.valueOf(TuTien.getApi().getTuLuyenSessionSeconds(player.getUniqueId()));
        }
        else if (params.equalsIgnoreCase("tuluyen_session_time")) {
            return formatDurationHms(TuTien.getApi().getTuLuyenSessionSeconds(player.getUniqueId()));
        }
        else if (params.equalsIgnoreCase("tuluyen_session_compact")) {
            return formatDurationCompact(TuTien.getApi().getTuLuyenSessionSeconds(player.getUniqueId()));
        }
        else if (params.equalsIgnoreCase("tuluyen_session_format")
                || params.equalsIgnoreCase("tuluyen_session_formatted")) {
            return formatConfiguredDuration(plugin.getConfig(), TuTien.getApi().getTuLuyenSessionSeconds(player.getUniqueId()));
        }

        // ==========================================
        // Cảnh Giới Placeholder (MAIN)
        // ==========================================
        else if (params.equalsIgnoreCase("canhgioi_full")) {
            // Returns: §a「Luyện Khí」 — display-name từ realms.yml (translated § codes)
            // Dùng cho: LuckPerms prefix, chat, scoreboard, TAB, v.v.
            return realmManager.getPlayerDisplayName(player.getUniqueId());
        }

        // ==========================================
        // Cảnh Giới Placeholders (chi tiết)
        // ==========================================
        else if (params.equalsIgnoreCase("canhgioi")) {
            // Returns: Luyện Khí (tên thuần, không màu)
            Realm realm = realmManager.getPlayerCurrentRealm(player.getUniqueId());
            return realm != null ? realm.getName() : "Phàm Nhân";
        }
        else if (params.equalsIgnoreCase("canhgioi_id")) {
            // Returns: 2
            PlayerRealm pr = realmManager.getPlayerRealm(player.getUniqueId());
            return String.valueOf(pr.getRealmId());
        }
        else if (params.equalsIgnoreCase("canhgioi_english")) {
            // Returns: Qi Refinement
            Realm realm = realmManager.getPlayerCurrentRealm(player.getUniqueId());
            return realm != null ? realm.getEnglishName() : "Mortal";
        }
        else if (params.equalsIgnoreCase("canhgioi_tang")) {
            // Returns: Đỉnh Phong (tầng nhỏ hiện tại)
            return realmManager.getPlayerSubRealmName(player.getUniqueId());
        }
        else if (params.equalsIgnoreCase("canhgioi_daigioi")) {
            // Returns: Phàm Giới / Tiên Giới / Thần Giới
            Realm realm = realmManager.getPlayerCurrentRealm(player.getUniqueId());
            return realm != null ? realm.getTier().getDisplayName() : "Phàm Giới";
        }
        else if (params.equalsIgnoreCase("dotpha_cooldown")) {
            PlayerRealm pr = realmManager.getPlayerRealm(player.getUniqueId());
            return String.valueOf(pr.getRemainingCooldownSeconds());
        }
        else if (params.equalsIgnoreCase("dotpha_ready")) {
            return formatDotPhaReady(plugin.getConfig(), isReadyForNextBreakthrough(player.getUniqueId()));
        }
        else if (params.equalsIgnoreCase("dotpha_next_tuvi_required")
                || params.equalsIgnoreCase("dotpha_next_tuvi_required_formatted")) {
            return getNextTuViRequired(plugin.getConfig(), player.getUniqueId());
        }

        // ==========================================
        // Top Placeholders
        // ==========================================
        else if (params.startsWith("top_tuluyen_")) {
            return handleTopTuLuyen(params.substring("top_tuluyen_".length()));
        }
        else if (params.startsWith("top_")) {
            String[] parts = params.split("_");
            if (parts.length >= 3) {
                String type = parts[1]; // name or tuvi
                int formatType = 0; // 0 normal, 1 int, 2 formatted, 3 compact
                int rank = -1;
                
                try {
                    if (parts.length == 3) {
                        rank = Integer.parseInt(parts[2]);
                    } else if (parts.length == 4) {
                        String f = parts[2];
                        if (f.equals("int")) formatType = 1;
                        else if (f.equals("formatted")) formatType = 2;
                        else if (f.equals("compact")) formatType = 3;
                        rank = Integer.parseInt(parts[3]);
                    }
                } catch (NumberFormatException e) {
                    return null;
                }

                if (rank <= 0) return "";
                int index = rank - 1;

                java.util.List<java.util.Map.Entry<String, Double>> topList = TuTien.getApi().getTopTuVi();
                if (index >= topList.size()) {
                    return type.equals("name") ? "---" : "0";
                }

                java.util.Map.Entry<String, Double> entry = topList.get(index);
                if (type.equals("name")) {
                    return entry.getKey();
                } else if (type.equals("tuvi")) {
                    double v = entry.getValue();
                    if (formatType == 0) return String.valueOf(v);
                    else if (formatType == 1) return String.valueOf((long) v);
                    else if (formatType == 2) return String.format("%,.0f", v);
                    else if (formatType == 3) return formatCompact(v);
                }
            }
        }

        return null; // Unknown placeholder
    }

    private String handleTopTuLuyen(String params) {
        String[] parts = params.split("_");
        if (parts.length < 2) {
            return null;
        }

        int rank;
        try {
            rank = Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (rank <= 0) {
            return "";
        }

        StringBuilder typeBuilder = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length - 1; i++) {
            typeBuilder.append('_').append(parts[i]);
        }
        String type = typeBuilder.toString().toLowerCase(java.util.Locale.ROOT);

        java.util.List<java.util.Map.Entry<String, Long>> topList = TuTien.getApi().getTopTuLuyenTime();
        int index = rank - 1;
        String emptyName = plugin.getConfig().getString("placeholders.tuluyen-time.empty-name", "---");
        long seconds = 0L;
        String name = emptyName;
        if (index < topList.size()) {
            java.util.Map.Entry<String, Long> entry = topList.get(index);
            name = entry.getKey();
            seconds = Math.max(0L, entry.getValue());
        }

        if (type.equals("name")) {
            return name;
        }
        if (type.equals("seconds") || type.equals("time_seconds")) {
            return String.valueOf(seconds);
        }
        if (type.equals("time") || type.equals("hms")) {
            return formatDurationHms(seconds);
        }
        if (type.equals("compact")) {
            return formatDurationCompact(seconds);
        }
        if (type.equals("format") || type.equals("formatted")) {
            return formatTopTuLuyen(plugin.getConfig(), rank, name, seconds);
        }

        return null;
    }

    private boolean isReadyForNextBreakthrough(UUID uuid) {
        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
            SubRealm nextSub = pr.getSubRealm().next();
            return nextSub != null && realmManager.checkSubRealmBreakthroughConditions(uuid, nextSub).isEmpty();
        }
        return realmManager.getNextRealm(uuid) != null && realmManager.checkBreakthroughConditions(uuid).isEmpty();
    }

    private String getNextTuViRequired(FileConfiguration config, UUID uuid) {
        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
        if (currentRealm == null) {
            return formatNextTuViRequired(config, "0");
        }

        String value;
        if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
            SubRealm nextSub = pr.getSubRealm().next();
            value = nextSub != null ? String.format("%,d", currentRealm.getTuViForSubRealm(nextSub)) : "0";
        } else {
            Realm nextRealm = realmManager.getNextRealm(uuid);
            value = nextRealm != null ? String.format("%,d", nextRealm.getTuViRequired()) : "0";
        }
        return formatNextTuViRequired(config, value);
    }

    static String formatDotPhaReady(FileConfiguration config, boolean ready) {
        String path = ready
                ? "placeholders.dotpha-ready.ready-display-name"
                : "placeholders.dotpha-ready.not-ready-display-name";
        String fallback = ready ? "V" : "X";
        return ChatColor.translateAlternateColorCodes('&', config.getString(path, fallback));
    }

    static String formatNextTuViRequired(FileConfiguration config, String value) {
        String displayName = config.getString("placeholders.dotpha-next-tuvi-required.display-name", "{value}");
        return ChatColor.translateAlternateColorCodes('&', displayName.replace("{value}", value));
    }

    static String formatConfiguredDuration(FileConfiguration config, long totalSeconds) {
        String displayName = config.getString("placeholders.tuluyen-time.format", "{compact}");
        return ChatColor.translateAlternateColorCodes('&', applyDurationPlaceholders(displayName, totalSeconds));
    }

    static String formatTopTuLuyen(FileConfiguration config, int rank, String name, long totalSeconds) {
        String displayName = config.getString("placeholders.tuluyen-time.top-format",
                "&e#{rank} &f{name} &7- &a{compact}");
        return ChatColor.translateAlternateColorCodes('&',
                applyDurationPlaceholders(displayName, totalSeconds)
                        .replace("{rank}", String.valueOf(rank))
                        .replace("{name}", name == null || name.isBlank()
                                ? config.getString("placeholders.tuluyen-time.empty-name", "---")
                                : name));
    }

    private static String applyDurationPlaceholders(String text, long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long days = safeSeconds / 86400L;
        long hoursOfDay = (safeSeconds % 86400L) / 3600L;
        long minutesOfHour = (safeSeconds % 3600L) / 60L;
        long secondsOfMinute = safeSeconds % 60L;
        long totalHours = safeSeconds / 3600L;
        long totalMinutes = safeSeconds / 60L;

        return text
                .replace("{total_seconds}", String.valueOf(safeSeconds))
                .replace("{seconds_total}", String.valueOf(safeSeconds))
                .replace("{total_minutes}", String.valueOf(totalMinutes))
                .replace("{minutes_total}", String.valueOf(totalMinutes))
                .replace("{total_hours}", String.valueOf(totalHours))
                .replace("{hours_total}", String.valueOf(totalHours))
                .replace("{days}", String.valueOf(days))
                .replace("{hours}", String.valueOf(hoursOfDay))
                .replace("{minutes}", String.valueOf(minutesOfHour))
                .replace("{seconds}", String.valueOf(secondsOfMinute))
                .replace("{hh}", pad2(totalHours))
                .replace("{mm}", pad2(minutesOfHour))
                .replace("{ss}", pad2(secondsOfMinute))
                .replace("{hms}", formatDurationHms(safeSeconds))
                .replace("{compact}", formatDurationCompact(safeSeconds));
    }

    private static String pad2(long value) {
        return value < 10L ? "0" + value : String.valueOf(value);
    }

    private String formatCompact(double number) {
        return RealmManager.formatNumber((long) number);
    }

    private static String formatDurationHms(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long hours = safeSeconds / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long seconds = safeSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatDurationCompact(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long days = safeSeconds / 86400L;
        long hours = (safeSeconds % 86400L) / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long seconds = safeSeconds % 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
