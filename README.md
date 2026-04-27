# 🐉 TuTienCore

> Hệ thống Tu Tiên (Cultivation) core plugin cho Minecraft server — **TurtleMC SkyblockDragon**.

[![Java](https://img.shields.io/badge/Java-21-orange)]()
[![Paper](https://img.shields.io/badge/Paper-1.21.4-blue)]()
[![License](https://img.shields.io/badge/License-Private-red)]()

---

## 📋 Tổng Quan

**TuTienCore** là plugin core quản lý toàn bộ hệ thống tu luyện cho server Skyblock Tu Tiên. Plugin cung cấp framework cảnh giới, tu vi, đột phá, và tích hợp sâu với hệ sinh thái MMO (MMOCore, MMOItems, MythicLib).

### Tính Năng Chính

- **19 Đại Cảnh Giới** — từ Phàm Nhân → Hồng Mông, mỗi cảnh giới 5 tầng nhỏ (95 bậc tu luyện)
- **3 Đại Giới** — 🟢 Phàm Giới (1-7) → 🔵 Tiên Giới (8-13) → 🟣 Thần Giới (14-19)
- **Realm Stat Bonus** — mỗi cảnh giới tăng 13 chỉ số MythicLib (HP, ATK, DEF, Crit, Mana...)
- **Thiên Lôi Kiếp** ⚡ — đột phá cảnh giới với sét ngẫu nhiên trong vùng 40×40, bay lên trời, tỉ lệ 20%–95%
- **Tu Luyện AFK** 🧘 — ngồi thiền tại Linh Mạch với 7 lớp particle effect (spiral, burst, lightning, absorption...)
- **5 Class Tu Luyện** — Kiếm Tiên · Thiên Cơ · Kim Thân · Linh Dược · Vô Ảnh (Ngũ Hành tương sinh/khắc)
- **10 Tầng Dungeon** + 3 Bí Cảnh — từ Yêu Thú Lâm đến Hỗn Nguyên Giới
- **ModelEngine Integration** — animation đột phá với model 3D
- **MythicLib Integration** — stat modifier per realm (auto-apply khi lên cảnh giới)
- **PlaceholderAPI** — hỗ trợ đầy đủ placeholder cho scoreboard/tab
- **Full API** — cho phép plugin khác hook vào toàn bộ hệ thống

---

## 🏗️ Cấu Trúc Module

```
TuTienCore/
├── TuTienCore-api/          ← API interface (dùng để hook từ plugin khác)
├── TuTienCore-implement/    ← Logic xử lý chính
├── TuTienCore-plugin/       ← Entry point, shaded JAR output
└── stubs/                   ← Stub dependencies (MythicLib, MMOCore, MMOItems, MythicMobs)
```

---

## 🔨 Build

**Yêu cầu:** JDK 21, Maven 3.9+

```bash
mvn clean package -DskipTests
```

Output: `TuTienCore-plugin/target/TuTienCore.jar`

---

## 🌟 Hệ Thống Cảnh Giới

### 🟢 PHÀM GIỚI (Mortal Realm) — CG 1-7

| # | Cảnh Giới | Tu Vi YC | Thực Lực YC | Màu |
|---|-----------|----------|-------------|-----|
| 1 | **Phàm Nhân** | 0 | 0 | §7 Xám |
| 2 | **Luyện Khí** | 5,000 | 500 | §a Xanh lá |
| 3 | **Trúc Cơ** | 25,000 | 2,000 | §a Xanh lá |
| 4 | **Kim Đan** | 100,000 | 8,000 | §e Vàng |
| 5 | **Nguyên Anh** | 350,000 | 25,000 | §e Vàng |
| 6 | **Hóa Thần** | 1,000,000 | 80,000 | §5 Tím |
| 7 | **Luyện Hư** | 3,000,000 | 200,000 | §5 Tím |

### 🔵 TIÊN GIỚI (Immortal Realm) — CG 8-13

| # | Cảnh Giới | Tu Vi YC | Thực Lực YC | Màu |
|---|-----------|----------|-------------|-----|
| 8 | **Đại Thừa** | 8,000,000 | 500,000 | §b Xanh dương |
| 9 | **Độ Kiếp** | 20,000,000 | 800,000 | §b Xanh dương |
| 10 | **Tiên Nhân** | 50,000,000 | 1,500,000 | §3 Cyan đậm |
| 11 | **Thiên Tiên** | 120,000,000 | 3,000,000 | §3 Cyan đậm |
| 12 | **Kim Tiên** | 300,000,000 | 6,000,000 | §6 Cam |
| 13 | **Thái Ất** | 700,000,000 | 12,000,000 | §6 Cam |

### 🟣 THẦN GIỚI (Divine Realm) — CG 14-19

| # | Cảnh Giới | Tu Vi YC | Thực Lực YC | Màu |
|---|-----------|----------|-------------|-----|
| 14 | **Đại La** | 1,500,000,000 | 25,000,000 | §d§l Hồng bold |
| 15 | **Chuẩn Thánh** | 3,500,000,000 | 50,000,000 | §d§l Hồng bold |
| 16 | **Thánh Nhân** | 8,000,000,000 | 100,000,000 | §c§l Đỏ bold |
| 17 | **Thiên Đế** | 20,000,000,000 | 250,000,000 | §c§l Đỏ bold |
| 18 | **Đạo Tổ** | 50,000,000,000 | 600,000,000 | §4§l Đỏ đậm bold |
| 19 | **Hồng Mông** | 100,000,000,000 | 1,000,000,000 | §4§l Đỏ đậm bold |

### Tầng Nhỏ (Sub-Realms)

Mỗi Đại Cảnh Giới có **5 tầng nhỏ**: **Sơ Kỳ → Trung Kỳ → Hậu Kỳ → Đỉnh Phong → Viên Mãn**

### Realm Stat Bonus (MythicLib)

Mỗi cảnh giới tự động cộng **13 chỉ số** thông qua MythicLib StatModifier:

| Stat | CG 1 | CG 7 | CG 13 | CG 19 |
|------|-------|-------|--------|--------|
| Max Health | 0% | +20% | +58% | +130% |
| Attack Damage | 0% | +13% | +38% | +85% |
| Defense | 0% | +13% | +38% | +85% |
| Critical Strike | 0% | +6% | +14% | +28% |
| Max Mana | 0% | +20% | +58% | +130% |
| Movement Speed | 0% | +3% | +6% | +9% |
| ... | ... | ... | ... | ... |

---

## ⚡ Thiên Lôi Kiếp (Breakthrough)

Khi đủ điều kiện đột phá, người chơi phải vượt qua **Thiên Lôi Kiếp** — sét giáng từ trời trong khu vực 40×40 block.

### Cơ Chế

1. Player kích hoạt đột phá → **Levitation** bay lên trời
2. Sét đánh **ngẫu nhiên** trong vùng 40×40 xung quanh player
3. Số tia sét và damage tăng theo cảnh giới (10 → 36 tia)
4. **Sống sót** = thành công, **Chết** = thất bại (cooldown 1 giờ)

### Tỉ Lệ & Damage

| Đột Phá Lên | Số Tia | DMG/Tia | Tỉ Lệ |
|-------------|--------|---------|--------|
| Luyện Khí | 10 | 3 ❤ | 95% |
| Kim Đan | 12 | 3 ❤ | 90% |
| Hóa Thần | 14 | 4 ❤ | 80% |
| Đại Thừa | 18 | 4.5 ❤ | 70% |
| Tiên Nhân | 21 | 5 ❤ | 60% |
| Kim Tiên | 24 | 6 ❤ | 50% |
| Đại La | 27 | 6.5 ❤ | 40% |
| Thánh Nhân | 30 | 7 ❤ | 30% |
| **Hồng Mông** | **36** | **8 ❤** | **20%** |

> Roll thất bại → sét gây **x2 DMG** → gần như chắc chắn chết

### Hiệu Ứng

- 🌩️ Trời chuyển tối (thunder storm)
- ⚡ Sét màu tím/vàng tùy đại giới
- 📢 Broadcast toàn server
- 🎬 ModelEngine animation

---

## 🧘 Tu Luyện (Meditation)

Ngồi thiền tại Linh Mạch để nhận Tu Vi tự động với **7 lớp particle effect**:

| Layer | Hiệu Ứng | Mô Tả |
|-------|-----------|--------|
| 🌀 Spiral | Xoáy năng lượng | 2 vòng xoáy ngược chiều |
| 💥 Burst | Bùng nổ định kỳ | Flash mỗi 3 giây |
| ⚡ Lightning | Tia sét nhỏ | Sét ngẫu nhiên xung quanh |
| 🔮 Absorption | Hấp thụ linh khí | Particle bay vào người |
| ⭕ Ground Circle | Trận pháp mặt đất | Vòng tròn rune |
| 🏛️ Pillar | Cột sáng | Cột ánh sáng lên trời |
| ✨ Ambient | Bụi phát sáng | Particle môi trường |

---

## ⚔️ Hệ Thống Class (5 Nghề Tu Luyện)

> Chọn class tại **Cảnh Giới 2** (Luyện Khí). Chuyển class tại CG 9 và CG 13.

| Class | Ngũ Hành | Vai Trò | Vũ Khí | Độ Khó |
|-------|----------|---------|--------|--------|
| ⚔️ **Kiếm Tiên** | 🟡 Kim | Melee DPS | Kiếm, Đao | ⭐⭐ |
| 🔮 **Thiên Cơ** | 🔴 Hỏa | Magic DPS/CC | Pháp Bảo, Trượng | ⭐⭐⭐ |
| 🛡️ **Kim Thân** | 🟤 Thổ | Tank / Brawler | Quyền, Thương | ⭐ |
| 💚 **Linh Dược** | 🟢 Mộc | Support/Healer | Phù, Trượng | ⭐⭐⭐ |
| 🗡️ **Vô Ảnh** | 🔵 Thủy | Assassin / Burst | Đoản Đao, Ám Khí | ⭐⭐⭐⭐⭐ |

### Tương Khắc Ngũ Hành

```
🟡 Kim (Kiếm Tiên) ──▶ Khắc ──▶ 🟢 Mộc (Linh Dược)
🟢 Mộc (Linh Dược) ──▶ Khắc ──▶ 🟤 Thổ (Kim Thân)
🟤 Thổ (Kim Thân)  ──▶ Khắc ──▶ 🔵 Thủy (Vô Ảnh)
🔵 Thủy (Vô Ảnh)   ──▶ Khắc ──▶ 🔴 Hỏa (Thiên Cơ)
🔴 Hỏa (Thiên Cơ)  ──▶ Khắc ──▶ 🟡 Kim (Kiếm Tiên)
```

- **Đánh class bị khắc**: +15% DMG, bỏ qua 10% DEF
- **Party đủ 5 class (Ngũ Hành)**: +20% toàn stat + 10% Drop Rate

---

## 🏰 Hệ Thống Dungeon (10 Tầng + 3 Bí Cảnh)

| Tầng | Tên | CG Yêu Cầu | ⚔ Thực Lực | Boss |
|------|-----|-------------|------------|------|
| 1 | **Yêu Thú Lâm** 🌲 | Luyện Khí | 500 | Sói Vương |
| 2 | **Âm Hồn Động** 👻 | Luyện Khí HK | 1,500 | Quỷ Hồn |
| 3 | **Hỏa Diễm Sơn** 🔥 | Trúc Cơ | 3,000 | Viêm Ma |
| 4 | **Băng Phong Cốc** ❄️ | Trúc Cơ HK | 6,000 | Băng Long |
| 5 | **Huyết Nguyệt Đàn** 🩸 | Kim Đan | 12,000 | Huyết Tộc Chúa |
| 6 | **Lôi Đình Tháp** ⚡ | Kim Đan HK | 25,000 | Lôi Thú |
| 7 | **Vạn Độc Trì** ☠️ | Nguyên Anh | 50,000 | Độc Tôn |
| 8 | **Long Cốt Mộ** 🐉 | Nguyên Anh HK | 100,000 | Cổ Long |
| 9 | **Tiên Phế Đô** ✨ | Hóa Thần | 200,000 | Đọa Tiên |
| 10 | **Hỗn Nguyên Giới** 🌀 | Luyện Hư | 400,000 | Hỗn Nguyên Thú |

> **Không giới hạn lượt chơi** — nhưng drop rate giảm dần (Diminishing Returns)

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

## 🔗 Dependencies

| Plugin | Vai Trò | Bắt Buộc |
|--------|---------|----------|
| **MythicLib** | Stat system, modifier API | ✅ |
| **MMOCore** | Class, skill, level system | ✅ |
| **MMOItems** | Item/gear system | ❌ (Optional) |
| **MythicMobs** | Custom mob/boss | ❌ (Optional) |
| **ModelEngine** | 3D model animation | ❌ (Optional) |
| **PlaceholderAPI** | Placeholder support | ❌ (Optional) |

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

### Realm (Cảnh Giới)

```java
TuTienAPI api = TuTien.getApi();

int realmId = api.getRealmId(uuid);           // 1-19
Realm realm = api.getRealm(uuid);             // Realm object
SubRealm sub = api.getSubRealm(uuid);         // SO_KY, TRUNG_KY, ...
boolean isMax = api.isMaxRealm(uuid);         // Đã max chưa

api.setRealm(uuid, 5, SubRealm.DINH_PHONG);  // Admin: set realm
Realm realm3 = api.getRealmById(3);           // Trúc Cơ
Map<Integer, Realm> all = api.getAllRealms();  // Tất cả 19 realm
```

### Realm Display (Hiển Thị)

```java
String full = api.getRealmDisplay(uuid);       // §a[Luyện Khí — Đỉnh Phong]
String name = api.getRealmDisplayName(uuid);   // §a「Luyện Khí ♦ Đỉnh Phong」
String realm = api.getRealmName(uuid);         // §aLuyện Khí
String sub = api.getSubRealmName(uuid);        // Đỉnh Phong
String tier = api.getRealmTierName(uuid);      // Phàm Giới
```

### Breakthrough (Đột Phá)

```java
boolean inBt = api.isInBreakthrough(uuid);
boolean onCd = api.isOnBreakthroughCooldown(uuid);
long cdLeft = api.getBreakthroughCooldownRemaining(uuid);  // giây
boolean ready = api.canBreakthrough(uuid);
```

### Tu Luyện (Meditation)

```java
boolean meditating = api.isTuLuyen(uuid);
Collection<UUID> allMeditating = api.getTuLuyenPlayers();
```

---

## 🎯 Custom Events

TuTienCore fire 5 Bukkit Event để plugin khác lắng nghe:

| Event | Cancellable | Khi Nào |
|-------|-------------|---------|
| `RealmBreakthroughEvent` | ✅ | **TRƯỚC** khi đột phá bắt đầu |
| `RealmBreakthroughSuccessEvent` | ❌ | **SAU** khi đột phá thành công |
| `RealmBreakthroughFailEvent` | ❌ | Khi đột phá thất bại (chết) |
| `SubRealmAdvanceEvent` | ❌ | Lên tầng nhỏ (Sơ Kỳ → Trung Kỳ...) |
| `TuViGainEvent` | ✅ | Khi Tu Vi sắp được cộng |

### Ví dụ: VIP x2 Tu Vi

```java
@EventHandler
public void onTuViGain(TuViGainEvent event) {
    if (event.getSource().equals("dungeon") 
        && event.getPlayer().hasPermission("vip.x2tuvi")) {
        event.setAmount(event.getAmount() * 2.0);
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

## 📜 License

Private — TurtleMC Server exclusive.
