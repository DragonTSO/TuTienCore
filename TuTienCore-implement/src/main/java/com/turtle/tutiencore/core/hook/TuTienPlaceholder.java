package com.turtle.tutiencore.core.hook;

import com.turtle.tutiencore.api.TuTien;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class TuTienPlaceholder extends PlaceholderExpansion {

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

    private String formatCompact(double number) {
        if (number < 1000) return String.valueOf((long) number);
        int exp = (int) (Math.log(number) / Math.log(1000));
        char suffix = "kMGTPE".charAt(exp - 1);
        return String.format("%.1f%c", number / Math.pow(1000, exp), suffix);
    }
}
