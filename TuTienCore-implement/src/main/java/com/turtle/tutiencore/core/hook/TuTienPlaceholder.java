package com.turtle.tutiencore.core.hook;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.manager.RealmManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TuTienPlaceholder extends PlaceholderExpansion {

    private final RealmManager realmManager;

    public TuTienPlaceholder(RealmManager realmManager) {
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
            return isReadyForNextBreakthrough(player.getUniqueId()) ? "V" : "X";
        }
        else if (params.equalsIgnoreCase("dotpha_next_tuvi_required")
                || params.equalsIgnoreCase("dotpha_next_tuvi_required_formatted")) {
            return getNextTuViRequired(player.getUniqueId());
        }

        // ==========================================
        // Top Placeholders
        // ==========================================
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

    private boolean isReadyForNextBreakthrough(UUID uuid) {
        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
            SubRealm nextSub = pr.getSubRealm().next();
            return nextSub != null && realmManager.checkSubRealmBreakthroughConditions(uuid, nextSub).isEmpty();
        }
        return realmManager.getNextRealm(uuid) != null && realmManager.checkBreakthroughConditions(uuid).isEmpty();
    }

    private String getNextTuViRequired(UUID uuid) {
        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
        if (currentRealm == null) {
            return "0";
        }

        if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
            SubRealm nextSub = pr.getSubRealm().next();
            return nextSub != null ? String.format("%,d", currentRealm.getTuViForSubRealm(nextSub)) : "0";
        }

        Realm nextRealm = realmManager.getNextRealm(uuid);
        return nextRealm != null ? String.format("%,d", nextRealm.getTuViRequired()) : "0";
    }

    private String formatCompact(double number) {
        if (number < 1000) return String.valueOf((long) number);
        int exp = (int) (Math.log(number) / Math.log(1000));
        char suffix = "kMGTPE".charAt(exp - 1);
        return String.format("%.1f%c", number / Math.pow(1000, exp), suffix);
    }
}
