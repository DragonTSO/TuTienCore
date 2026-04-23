# 🐉 TuTienCore

> Hệ thống Tu Tiên (Cultivation) core plugin cho Minecraft server.

[![Java](https://img.shields.io/badge/Java-21-orange)]()
[![Paper](https://img.shields.io/badge/Paper-1.21.4-blue)]()
[![License](https://img.shields.io/badge/License-Private-red)]()

---

## 📋 Tính Năng

- **19 Đại Cảnh Giới** — từ Phàm Nhân → Hồng Mông, mỗi cảnh giới 5 tầng nhỏ (95 bậc tu luyện)
- **Hệ thống Tu Vi** — tích lũy kinh nghiệm tu luyện qua mọi hoạt động
- **Thiên Lôi Kiếp** — đột phá cảnh giới với hiệu ứng sét, tỉ lệ thành công giảm dần
- **Tu Luyện AFK** — ngồi thiền tại Linh Mạch để nhận Tu Vi tự động
- **ModelEngine Integration** — animation đột phá với model 3D
- **PlaceholderAPI** — hỗ trợ đầy đủ placeholder cho scoreboard/tab
- **Full API** — cho phép plugin khác hook vào toàn bộ hệ thống

---

## 🏗️ Cấu Trúc Module

```
TuTienCore/
├── TuTienCore-api/          ← API interface (dùng để hook từ plugin khác)
├── TuTienCore-implement/    ← Logic xử lý chính
└── TuTienCore-plugin/       ← Entry point, shaded JAR output
```

---

## 🔨 Build

**Yêu cầu:** JDK 21, Maven 3.9+

```bash
mvn clean package -DskipTests
```

Output: `TuTienCore-plugin/target/TuTienCore.jar`

---

## ⚙️ Commands

| Lệnh | Quyền | Mô Tả |
|-------|-------|--------|
| `/tuvi` | `tutien.tuvi` | Xem Tu Vi của mình |
| `/tuvi <player>` | `tutien.tuvi.others` | Xem Tu Vi người khác |
| `/tuvi set <player> <amount>` | `tutien.admin` | Set Tu Vi |
| `/tuvi add <player> <amount>` | `tutien.admin` | Cộng Tu Vi |
| `/tuvi take <player> <amount>` | `tutien.admin` | Trừ Tu Vi |
| `/tuvi top` | `tutien.tuvi` | Bảng xếp hạng Tu Vi |
| `/canhgioi` | `tutien.canhgioi` | Xem cảnh giới hiện tại |
| `/canhgioi set <player> <id> [sub]` | `tutien.admin` | Set cảnh giới |
| `/dotpha` | `tutien.dotpha` | Mở GUI đột phá |
| `/tuluyen` | `tutien.tuluyen` | Bắt đầu tu luyện |
| `/ttc reload` | `tutien.admin` | Reload config |

---

## 🔌 PlaceholderAPI

| Placeholder | Ví Dụ Output |
|-------------|-------------|
| `%tutien_tuvi%` | `125000` |
| `%tutien_tuvi_formatted%` | `125.0K` |
| `%tutien_canhgioi_id%` | `3` |
| `%tutien_canhgioi_name%` | `Trúc Cơ` |
| `%tutien_canhgioi_full%` | `§9「Trúc Cơ ♦ Đỉnh Phong」` |
| `%tutien_canhgioi_tang%` | `Đỉnh Phong` |
| `%tutien_canhgioi_daigioi%` | `Phàm Giới` |
| `%tutien_dotpha_ready%` | `true` / `false` |

---

# 📦 API Documentation

## Cách Hook từ Plugin Khác

### 1. Thêm dependency

```xml
<dependency>
    <groupId>com.turtle</groupId>
    <artifactId>TuTienCore-api</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### 2. Khai báo depend

```yaml
# plugin.yml
depend: [TuTienCore]
# hoặc softdepend nếu optional
softdepend: [TuTienCore]
```

### 3. Sử dụng API

```java
import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.TuTienAPI;

TuTienAPI api = TuTien.getApi();
double tuvi = api.getTuVi(player.getUniqueId());
```

---

## API Methods

### Tu Vi (Cultivation Points)

```java
TuTienAPI api = TuTien.getApi();

// Đọc
double tuvi = api.getTuVi(uuid);
List<Map.Entry<String, Double>> top = api.getTopTuVi();

// Ghi
api.setTuVi(uuid, 50000);
api.addTuVi(uuid, 1000);
api.takeTuVi(uuid, 500);
```

| Method | Return | Mô Tả |
|--------|--------|--------|
| `getTuVi(UUID)` | `double` | Lấy Tu Vi hiện tại |
| `setTuVi(UUID, double)` | `void` | Set Tu Vi |
| `addTuVi(UUID, double)` | `void` | Cộng thêm Tu Vi |
| `takeTuVi(UUID, double)` | `void` | Trừ Tu Vi (min 0) |
| `getTopTuVi()` | `List<Entry<String, Double>>` | Top Tu Vi toàn server |

### Realm (Cảnh Giới)

```java
TuTienAPI api = TuTien.getApi();

int realmId = api.getRealmId(uuid);           // 1-19
Realm realm = api.getRealm(uuid);             // Realm object
SubRealm sub = api.getSubRealm(uuid);         // SO_KY, TRUNG_KY, ...
boolean isMax = api.isMaxRealm(uuid);         // Đã max chưa

// Admin: set realm trực tiếp
api.setRealm(uuid, 5, SubRealm.DINH_PHONG);

// Lấy Realm theo ID
Realm realm3 = api.getRealmById(3);           // Trúc Cơ
Map<Integer, Realm> all = api.getAllRealms();  // Tất cả 19 realm
```

| Method | Return | Mô Tả |
|--------|--------|--------|
| `getRealmId(UUID)` | `int` | ID cảnh giới (1-19) |
| `getRealm(UUID)` | `Realm` | Object Realm hiện tại |
| `getRealmById(int)` | `Realm` | Lấy Realm theo ID |
| `getAllRealms()` | `Map<Integer, Realm>` | Toàn bộ realms |
| `getMaxRealmId()` | `int` | ID realm cao nhất (mặc định 19) |
| `getPlayerRealmData(UUID)` | `PlayerRealm` | Dữ liệu realm + sub-realm + cooldown |
| `getSubRealm(UUID)` | `SubRealm` | Tầng nhỏ hiện tại |
| `setRealm(UUID, int, SubRealm)` | `void` | Set realm (admin/debug) |
| `isMaxRealm(UUID)` | `boolean` | Đã max realm chưa |

### Realm Display (Hiển Thị)

```java
TuTienAPI api = TuTien.getApi();

String full = api.getRealmDisplay(uuid);       // §a[Luyện Khí — Đỉnh Phong]
String name = api.getRealmDisplayName(uuid);   // §a「Luyện Khí ♦ Đỉnh Phong」
String realm = api.getRealmName(uuid);         // §aLuyện Khí
String sub = api.getSubRealmName(uuid);        // Đỉnh Phong
String tier = api.getRealmTierName(uuid);      // Phàm Giới
```

| Method | Return | Ví Dụ Output |
|--------|--------|-------------|
| `getRealmDisplay(UUID)` | `String` | `§a[Luyện Khí — Đỉnh Phong]` |
| `getRealmDisplayName(UUID)` | `String` | `§a「Luyện Khí ♦ Đỉnh Phong」` |
| `getRealmName(UUID)` | `String` | `§aLuyện Khí` |
| `getSubRealmName(UUID)` | `String` | `Đỉnh Phong` |
| `getRealmTierName(UUID)` | `String` | `Phàm Giới` / `Tiên Giới` / `Thần Giới` |

### Breakthrough (Đột Phá)

```java
TuTienAPI api = TuTien.getApi();

boolean inBt = api.isInBreakthrough(uuid);
boolean onCd = api.isOnBreakthroughCooldown(uuid);
long cdLeft = api.getBreakthroughCooldownRemaining(uuid);  // giây
boolean ready = api.canBreakthrough(uuid);
```

| Method | Return | Mô Tả |
|--------|--------|--------|
| `isInBreakthrough(UUID)` | `boolean` | Đang trong Thiên Lôi Kiếp? |
| `isOnBreakthroughCooldown(UUID)` | `boolean` | Đang cooldown? |
| `getBreakthroughCooldownRemaining(UUID)` | `long` | Giây cooldown còn lại |
| `canBreakthrough(UUID)` | `boolean` | Đủ mọi điều kiện đột phá? |

### Tu Luyện (Meditation)

```java
TuTienAPI api = TuTien.getApi();

boolean meditating = api.isTuLuyen(uuid);
Collection<UUID> allMeditating = api.getTuLuyenPlayers();
```

| Method | Return | Mô Tả |
|--------|--------|--------|
| `isTuLuyen(UUID)` | `boolean` | Đang ngồi thiền? |
| `getTuLuyenPlayers()` | `Collection<UUID>` | Tất cả player đang tu luyện |

### Utility

```java
String formatted = api.formatNumber(1500000);  // "1.5M"
String formatted2 = api.formatNumber(25000);   // "25.0K"
```

---

## 🎯 Custom Events

TuTienCore fire 5 Bukkit Event để plugin khác lắng nghe và phản ứng.

### `RealmBreakthroughEvent` ✅ Cancellable

> Fired **TRƯỚC** khi đột phá Đại Cảnh Giới bắt đầu. Cancel để chặn.

```java
import com.turtle.tutiencore.api.event.RealmBreakthroughEvent;

@EventHandler
public void onBreakthrough(RealmBreakthroughEvent event) {
    Player player = event.getPlayer();
    Realm from = event.getFromRealm();   // Realm hiện tại
    Realm to = event.getToRealm();       // Realm muốn đột phá

    // Chặn đột phá nếu đang trong dungeon
    if (isInDungeon(player)) {
        event.setCancelled(true);
        player.sendMessage("§cKhông thể đột phá trong dungeon!");
    }
}
```

### `RealmBreakthroughSuccessEvent`

> Fired **SAU** khi đột phá Đại Cảnh Giới thành công.

```java
import com.turtle.tutiencore.api.event.RealmBreakthroughSuccessEvent;

@EventHandler
public void onSuccess(RealmBreakthroughSuccessEvent event) {
    Player player = event.getPlayer();
    Realm oldRealm = event.getFromRealm();
    Realm newRealm = event.getToRealm();

    // Thưởng Linh Thạch khi đột phá thành công
    giveReward(player, newRealm.getId() * 1000);

    // Update LuckPerms prefix
    updatePrefix(player, newRealm);
}
```

### `RealmBreakthroughFailEvent`

> Fired khi đột phá Đại Cảnh Giới **thất bại** (chết trong Thiên Lôi Kiếp).

```java
import com.turtle.tutiencore.api.event.RealmBreakthroughFailEvent;

@EventHandler
public void onFail(RealmBreakthroughFailEvent event) {
    Player player = event.getPlayer();
    Realm current = event.getCurrentRealm();   // Realm vẫn giữ
    Realm target = event.getTargetRealm();     // Realm muốn đạt

    // Ghi log
    logBreakthroughFail(player, target);
}
```

### `SubRealmAdvanceEvent`

> Fired khi player lên **tầng nhỏ** (Sơ Kỳ → Trung Kỳ → ... → Viên Mãn).

```java
import com.turtle.tutiencore.api.event.SubRealmAdvanceEvent;

@EventHandler
public void onSubRealmUp(SubRealmAdvanceEvent event) {
    Player player = event.getPlayer();
    Realm realm = event.getRealm();
    SubRealm from = event.getFromSubRealm();
    SubRealm to = event.getToSubRealm();

    // Mở khóa tính năng khi đạt Viên Mãn
    if (to == SubRealm.VIEN_MAN) {
        unlockBreakthroughButton(player);
    }
}
```

### `TuViGainEvent` ✅ Cancellable

> Fired khi Tu Vi sắp được cộng. Có thể **thay đổi số lượng** hoặc **chặn hoàn toàn**.

```java
import com.turtle.tutiencore.api.event.TuViGainEvent;

@EventHandler
public void onTuViGain(TuViGainEvent event) {
    Player player = event.getPlayer();
    double amount = event.getAmount();
    String source = event.getSource();   // "dungeon", "mine", "farm", "tuluyen", "command"

    // VIP x2 Tu Vi từ dungeon
    if (source.equals("dungeon") && player.hasPermission("vip.x2tuvi")) {
        event.setAmount(amount * 2.0);
    }

    // Chặn Tu Vi khi bị phạt
    if (isPunished(player)) {
        event.setCancelled(true);
    }
}
```

---

## 📂 API Package Structure

```
com.turtle.tutiencore.api/
│
├── TuTien.java                              ← Static entry: TuTien.getApi()
├── TuTienAPI.java                           ← Main API interface (25+ methods)
│
├── event/
│   ├── RealmBreakthroughEvent.java          ← Pre-breakthrough (cancellable)
│   ├── RealmBreakthroughSuccessEvent.java   ← Post-success
│   ├── RealmBreakthroughFailEvent.java      ← Post-failure
│   ├── SubRealmAdvanceEvent.java            ← Sub-realm level up
│   └── TuViGainEvent.java                  ← Tu Vi gain (cancellable)
│
└── realm/
    ├── Realm.java                           ← Realm data object
    ├── PlayerRealm.java                     ← Player's realm state
    ├── RealmTier.java                       ← PHAM_GIOI / TIEN_GIOI / THAN_GIOI
    └── SubRealm.java                        ← SO_KY → VIEN_MAN (5 tầng)
```

---

## 💡 Ví Dụ Tích Hợp

### Gate Dungeon theo Cảnh Giới

```java
public boolean canEnterDungeon(Player player, int dungeonTier) {
    TuTienAPI api = TuTien.getApi();
    int realmId = api.getRealmId(player.getUniqueId());

    // Dungeon tier mapping: T1=realm2, T2=realm2, T3=realm3...
    int requiredRealm = Math.max(2, dungeonTier);
    return realmId >= requiredRealm;
}
```

### Tính Combat Power

```java
public long calculateCombatPower(Player player) {
    TuTienAPI api = TuTien.getApi();
    UUID uuid = player.getUniqueId();

    double tuvi = api.getTuVi(uuid);
    int realmId = api.getRealmId(uuid);
    Realm realm = api.getRealm(uuid);

    long realmBonus = realmId * 5000L;
    long tuviBonus = (long)(tuvi / 200.0);

    return realmBonus + tuviBonus;
}
```

### Auto-update LuckPerms Prefix

```java
@EventHandler
public void onRealmChange(RealmBreakthroughSuccessEvent event) {
    Player player = event.getPlayer();
    TuTienAPI api = TuTien.getApi();

    String prefix = api.getRealmDisplayName(player.getUniqueId());
    // → "§a「Luyện Khí」"

    // Set LuckPerms prefix...
    LuckPermsProvider.get().getUserManager()
        .modifyUser(player.getUniqueId(), user -> {
            user.data().clear(NodeType.PREFIX::matches);
            user.data().add(PrefixNode.builder(prefix, 100).build());
        });
}
```

---

## 📜 License

Private — TurtleMC Server exclusive.
