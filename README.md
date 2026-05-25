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

Aliases được đọc từ `config.yml > commands`. Mặc định: `/tl` cho `/tuluyen`, `/dp` cho `/dotpha`, `/realm` cho `/canhgioi`.

| Lệnh | Quyền | Mô Tả |
|-------|-------|--------|
| `/ttc` | none | Xem help chính |
| `/ttc tuluyen` | none | Toggle tu luyện, cùng logic với `/tuluyen` |
| `/ttc reload` | `tutiencore.admin` | Reload config, realm, GUI, phi kiếm, hologram, action bar, Nhập Thần và aliases |
| `/ttc wand` | `tutiencore.admin` | Nhận wand chọn vùng tu luyện |
| `/ttc create <zoneName>` | `tutiencore.admin` | Tạo vùng tu luyện từ pos1/pos2 |
| `/ttc zonecenter <zoneName>` | `tutiencore.admin` | Đặt tâm particle cho vùng tu luyện |
| `/ttc admin tuvi <give\|remove\|set\|check\|reset\|resetall> <player> [amount]` | `tutiencore.admin` | Lệnh Tu Vi legacy trong `/ttc` |
| `/tuluyen` | `tutiencore.use` | Bật/tắt trạng thái tu luyện |
| `/dotpha` | `tutiencore.use` | Mở GUI đột phá cảnh giới |
| `/canhgioi` | `tutiencore.use` | Mở GUI danh sách cảnh giới |
| `/canhgioi set <player> <realm_id> [sub_realm]` | `tutiencore.admin` | Set cảnh giới và tầng nhỏ |
| `/canhgioi info <player>` | `tutiencore.admin` | Xem dữ liệu cảnh giới người chơi |
| `/canhgioi list` | `tutiencore.admin` | Liệt kê tất cả cảnh giới |
| `/canhgioi reload` | `tutiencore.admin` | Reload `realms.yml` và GUI cảnh giới |
| `/tuvi set <player> <amount>` | `tutiencore.admin` | Set Tu Vi |
| `/tuvi add <player> <amount>` | `tutiencore.admin` | Cộng Tu Vi, có cap theo mốc đột phá kế tiếp |
| `/tuvi take <player> <amount>` | `tutiencore.admin` | Trừ Tu Vi, không xuống dưới 0 |
| `/tuvi reset <player>` | `tutiencore.admin` | Reset Tu Vi về 0 |
| `/tuvi resetall` | `tutiencore.admin` | Reset Tu Vi người chơi online |
| `/tuvi info <player>` | `tutiencore.admin` | Xem Tu Vi người chơi |
| `/nhapthan` | `tutiencore.use` | Mở GUI Nhập Thần |
| `/nhapthan give <player\|uuid> <type> <rarity>` | `tutiencore.nhapthan.give` | Cấp Nhập Thần cho player online hoặc player đã cache |

### Permissions

| Permission | Default | Mô Tả |
|------------|---------|-------|
| `tutiencore.use` | true | Dùng lệnh player: `/tuluyen`, `/dotpha`, `/canhgioi`, `/nhapthan` |
| `tutiencore.admin` | op | Dùng lệnh admin |
| `tutiencore.nhapthan.give` | op | Cấp Nhập Thần bằng command |
| `tutiencore.tuluyen.vip` | false | Tích Tu Vi offline và claim GUI; có thể đổi bằng `offline-tuluyen.permission` |
| `tutiencore.tuvi.bonus.<percent>` | unset | Bonus Tu Vi khi tu luyện, ví dụ `tutiencore.tuvi.bonus.50` |
| `tutiencore.bypass.realm_requirement` | unset | Bỏ qua yêu cầu cảnh giới của MMOItems custom stat |

---

## 🔌 PlaceholderAPI

Identifier: `tutien`.

| Placeholder | Output |
|-------------|--------|
| `%tutien_tuvi%` | Tu Vi dạng số thực |
| `%tutien_tuvi_int%` | Tu Vi dạng số nguyên |
| `%tutien_tuvi_formatted%` | Tu Vi có dấu phẩy, ví dụ `1,500` |
| `%tutien_tuvi_compact%` | Tu Vi rút gọn, ví dụ `1.5k`, `2.0M` |
| `%tutien_canhgioi_full%` | Display name từ `realms.yml`, có màu |
| `%tutien_canhgioi%` | Tên cảnh giới không màu |
| `%tutien_canhgioi_id%` | ID cảnh giới hiện tại |
| `%tutien_canhgioi_english%` | Tên tiếng Anh của cảnh giới |
| `%tutien_canhgioi_tang%` | Tầng nhỏ hiện tại |
| `%tutien_canhgioi_daigioi%` | Đại giới: `Phàm Giới`, `Tiên Giới`, `Thần Giới` |
| `%tutien_dotpha_cooldown%` | Cooldown đột phá còn lại, đơn vị giây |
| `%tutien_dotpha_ready%` | Output cấu hình tại `placeholders.dotpha-ready` |
| `%tutien_dotpha_next_tuvi_required%` | Tu Vi cần cho mốc đột phá kế tiếp |
| `%tutien_dotpha_next_tuvi_required_formatted%` | Alias cùng output với placeholder trên |
| `%tutien_top_name_<rank>%` | Tên người chơi ở hạng `<rank>` |
| `%tutien_top_tuvi_<rank>%` | Tu Vi hạng `<rank>` dạng số thực |
| `%tutien_top_tuvi_int_<rank>%` | Tu Vi hạng `<rank>` dạng số nguyên |
| `%tutien_top_tuvi_formatted_<rank>%` | Tu Vi hạng `<rank>` có dấu phẩy |
| `%tutien_top_tuvi_compact_<rank>%` | Tu Vi hạng `<rank>` rút gọn |

---

## 🔗 Dependencies

| Plugin | Vai Trò | Bắt Buộc |
|--------|---------|----------|
| **TurtleCore** | Core dependency nội bộ | ✅ |
| **ProtocolLib** | Packet/nameplate/action-bar support | ✅ |
| **packetevents** | Packet event runtime | ✅ |
| **PlaceholderAPI** | Placeholder expansion `%tutien_*%` | ❌ |
| **Vault** | Đọc tiền khi cấu hình yêu cầu money | ❌ |
| **PlayerPoints** | Chi phí claim offline x2 | ❌ |
| **ModelEngine** | Model/animation khi tu luyện | ❌ |
| **MythicMobs** | Hook gameplay/mob tùy server | ❌ |
| **MMOItems** | Custom item stats và realm requirement | ❌ |
| **MMOCore** | Tích hợp mana/action bar/stat ecosystem | ❌ |
| **FancyHolograms** | Hologram khi tu luyện | ❌ |
| **WorldGuard** | Bonus môi trường theo region | ❌ |
| **BetterteamsAddon** | Bonus tông môn/team khi tu luyện | ❌ |

### MMOItems Custom Stats

TuTienCore tự bootstrap các stat sau vào `plugins/MMOItems/custom-stats.yml` nếu thiếu:

| Stat ID | Kiểu | Mô Tả |
|---------|------|-------|
| `TUTIEN_REALM_REQUIREMENT` | text | Yêu cầu cảnh giới tối thiểu. Hỗ trợ `4` hoặc `4:trung-ky` |
| `MAX_HEALTH_PERCENT` | double | Cộng phần trăm `MAX_HEALTH` qua MythicLib |
| `HEALTH_REGENERATION` | double | Stat tương thích set bonus MMOCore |
| `MAX_HEALTH_REGENERATION` | double | Stat tương thích set bonus MMOCore |

---

# 📦 API Documentation

API ổn định nằm trong module `TuTienCore-api`, package `com.turtle.tutiencore.api.*`. Các class trong `com.turtle.tutiencore.core.*` là implementation nội bộ, không nên depend trực tiếp từ plugin khác.

Public signatures bên dưới đã được verify bằng `javap -classpath TuTienCore-api/target/classes -public ...` sau khi build module API.

## Cách Hook Từ Plugin Khác

### Maven dependency

```xml
<dependency>
    <groupId>com.turtle</groupId>
    <artifactId>TuTienCore-api</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### plugin.yml

```yaml
depend: [TuTienCore]
```

Nếu hook là optional:

```yaml
softdepend: [TuTienCore]
```

### Entry point

```java
import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.TuTienAPI;

TuTienAPI api = TuTien.getApi();
if (api == null) {
    return;
}

double tuVi = api.getTuVi(player.getUniqueId());
```

`TuTien.getApi()` được set khi TuTienCore enable. `TuTien.setApi(...)` là API nội bộ cho TuTienCore, plugin khác không nên gọi.

## API Package Structure

```text
com.turtle.tutiencore.api
├── TuTien.java
├── TuTienAPI.java
├── event
│   ├── RealmBreakthroughEvent.java
│   ├── RealmBreakthroughFailEvent.java
│   ├── RealmBreakthroughSuccessEvent.java
│   ├── SubRealmAdvanceEvent.java
│   └── TuViGainEvent.java
└── realm
    ├── PlayerRealm.java
    ├── Realm.java
    ├── RealmTier.java
    └── SubRealm.java
```

## Class: `com.turtle.tutiencore.api.TuTien`

Purpose: static singleton entry point.

Public methods:

| Method | Signature | Notes |
|--------|-----------|-------|
| Constructor | `public TuTien()` | Constructor mặc định do Java tạo; không cần dùng cho integration |
| `getApi` | `public static TuTienAPI getApi()` | Lấy API hiện tại; có thể `null` nếu gọi quá sớm hoặc TuTienCore chưa enable |
| `setApi` | `public static void setApi(TuTienAPI api)` | Nội bộ TuTienCore; ném `UnsupportedOperationException` nếu set lần hai |

## Interface: `com.turtle.tutiencore.api.TuTienAPI`

All methods are synchronous. Mutation methods update cache; TuTienCore flush dữ liệu khi player quit/plugin disable, và command nội bộ tự save sau khi đổi.

### Tu Vi

| Method | Signature | Mô Tả |
|--------|-----------|-------|
| `getTuVi` | `double getTuVi(UUID uuid)` | Đọc Tu Vi hiện tại |
| `setTuVi` | `void setTuVi(UUID uuid, double amount)` | Set thẳng Tu Vi, không fire event |
| `addTuVi` | `void addTuVi(UUID uuid, double amount)` | Cộng Tu Vi; amount dương bị cap tại mốc tầng/cảnh giới kế tiếp |
| `takeTuVi` | `void takeTuVi(UUID uuid, double amount)` | Trừ Tu Vi, min 0 |
| `getTopTuVi` | `List<Map.Entry<String, Double>> getTopTuVi()` | Lấy leaderboard theo tên player và Tu Vi |
| `formatNumber` | `String formatNumber(long number)` | Format số dạng gọn theo RealmManager |

### Cảnh Giới

| Method | Signature | Mô Tả |
|--------|-----------|-------|
| `getRealmId` | `int getRealmId(UUID uuid)` | ID cảnh giới hiện tại |
| `getRealm` | `Realm getRealm(UUID uuid)` | Realm hiện tại của player |
| `getRealmById` | `Realm getRealmById(int realmId)` | Realm theo ID |
| `getAllRealms` | `Map<Integer, Realm> getAllRealms()` | Map tất cả realm đã load |
| `getMaxRealmId` | `int getMaxRealmId()` | ID realm cao nhất |
| `getPlayerRealmData` | `PlayerRealm getPlayerRealmData(UUID uuid)` | Dữ liệu realm + tầng nhỏ + cooldown |
| `getSubRealm` | `SubRealm getSubRealm(UUID uuid)` | Tầng nhỏ hiện tại |
| `setRealm` | `void setRealm(UUID uuid, int realmId, SubRealm subRealm)` | Set cảnh giới trực tiếp, không fire breakthrough event |
| `isMaxRealm` | `boolean isMaxRealm(UUID uuid)` | Player đã ở cảnh giới tối đa chưa |

### Display

| Method | Signature | Mô Tả |
|--------|-----------|-------|
| `getRealmDisplay` | `String getRealmDisplay(UUID uuid)` | Display dạng `[Realm — SubRealm]`, có màu |
| `getRealmDisplayName` | `String getRealmDisplayName(UUID uuid)` | Display name từ `realms.yml`, có màu |
| `getRealmName` | `String getRealmName(UUID uuid)` | Tên realm có màu |
| `getSubRealmName` | `String getSubRealmName(UUID uuid)` | Tên tầng nhỏ |
| `getRealmTierName` | `String getRealmTierName(UUID uuid)` | Tên đại giới |

### Đột Phá Và Tu Luyện

| Method | Signature | Mô Tả |
|--------|-----------|-------|
| `isInBreakthrough` | `boolean isInBreakthrough(UUID uuid)` | Đang trong Thiên Lôi Kiếp |
| `isOnBreakthroughCooldown` | `boolean isOnBreakthroughCooldown(UUID uuid)` | Đang cooldown đột phá |
| `getBreakthroughCooldownRemaining` | `long getBreakthroughCooldownRemaining(UUID uuid)` | Cooldown còn lại, giây |
| `canBreakthrough` | `boolean canBreakthrough(UUID uuid)` | Đủ điều kiện đột phá mốc kế tiếp |
| `isTuLuyen` | `boolean isTuLuyen(UUID uuid)` | Player online có đang tu luyện không |
| `getTuLuyenPlayers` | `Collection<UUID> getTuLuyenPlayers()` | UUID player đang tu luyện |

## Class: `com.turtle.tutiencore.api.realm.Realm`

Purpose: data object cho một Đại Cảnh Giới.

Constructors:

```java
public Realm(int id, String name, String displayName, String englishName, RealmTier tier,
             long tuViRequired, long thucLucRequired, String color,
             long soKyTuVi, long trungKyTuVi, long hauKyTuVi,
             long dinhPhongTuVi, long vienManTuVi,
             int lightningBolts, double damagePerBolt, double successRate,
             Map<String, Double> statBonuses)

public Realm(int id, String name, String displayName, String englishName, RealmTier tier,
             long tuViRequired, long thucLucRequired, double moneyRequired, String color,
             long soKyTuVi, long trungKyTuVi, long hauKyTuVi,
             long dinhPhongTuVi, long vienManTuVi,
             int lightningBolts, double damagePerBolt, double successRate,
             Map<String, Double> statBonuses)
```

Public methods:

| Method | Signature |
|--------|-----------|
| `setSubRealmDisplayName` | `void setSubRealmDisplayName(SubRealm subRealm, String displayName)` |
| `setSubRealmRequirements` | `void setSubRealmRequirements(SubRealm subRealm, long thucLucRequired, double moneyRequired)` |
| `setSubRealmThucLucRequirement` | `void setSubRealmThucLucRequirement(SubRealm subRealm, long thucLucRequired)` |
| `setSubRealmMoneyRequirement` | `void setSubRealmMoneyRequirement(SubRealm subRealm, double moneyRequired)` |
| `getSubRealmDisplayNameTranslated` | `String getSubRealmDisplayNameTranslated(SubRealm subRealm)` |
| `getId` | `int getId()` |
| `getName` | `String getName()` |
| `getEnglishName` | `String getEnglishName()` |
| `getTier` | `RealmTier getTier()` |
| `getTuViRequired` | `long getTuViRequired()` |
| `getThucLucRequired` | `long getThucLucRequired()` |
| `getMoneyRequired` | `double getMoneyRequired()` |
| `getColor` | `String getColor()` |
| `getColorTranslated` | `String getColorTranslated()` |
| `getDisplayName` | `String getDisplayName()` |
| `getDisplayNameTranslated` | `String getDisplayNameTranslated()` |
| `getSoKyTuVi` | `long getSoKyTuVi()` |
| `getTrungKyTuVi` | `long getTrungKyTuVi()` |
| `getHauKyTuVi` | `long getHauKyTuVi()` |
| `getDinhPhongTuVi` | `long getDinhPhongTuVi()` |
| `getVienManTuVi` | `long getVienManTuVi()` |
| `getLightningBolts` | `int getLightningBolts()` |
| `getDamagePerBolt` | `double getDamagePerBolt()` |
| `getSuccessRate` | `double getSuccessRate()` |
| `getStatBonuses` | `Map<String, Double> getStatBonuses()` |
| `getStatBonus` | `double getStatBonus(String statName)` |
| `getTuViForSubRealm` | `long getTuViForSubRealm(SubRealm subRealm)` |
| `getThucLucForSubRealm` | `long getThucLucForSubRealm(SubRealm subRealm)` |
| `getMoneyForSubRealm` | `double getMoneyForSubRealm(SubRealm subRealm)` |
| `getFormattedName` | `String getFormattedName()` |
| `getFullDisplay` | `String getFullDisplay(SubRealm subRealm)` |
| `getTotalDamageSuccess` | `double getTotalDamageSuccess()` |
| `getTotalDamageFail` | `double getTotalDamageFail()` |
| `toString` | `String toString()` |

## Class: `com.turtle.tutiencore.api.realm.PlayerRealm`

Purpose: realm state của một player.

Public API:

| Method | Signature |
|--------|-----------|
| Constructor | `public PlayerRealm(int realmId, SubRealm subRealm)` |
| `getRealmId` | `int getRealmId()` |
| `getSubRealm` | `SubRealm getSubRealm()` |
| `getBreakthroughCooldown` | `long getBreakthroughCooldown()` |
| `setRealmId` | `void setRealmId(int realmId)` |
| `setSubRealm` | `void setSubRealm(SubRealm subRealm)` |
| `setBreakthroughCooldown` | `void setBreakthroughCooldown(long breakthroughCooldown)` |
| `isOnCooldown` | `boolean isOnCooldown()` |
| `getRemainingCooldownSeconds` | `long getRemainingCooldownSeconds()` |
| `applyCooldown` | `void applyCooldown(long durationMs)` |

Prefer `TuTienAPI#setRealm(...)` for external realm writes so dữ liệu được lưu theo path hiện có.

## Enums

### `com.turtle.tutiencore.api.realm.SubRealm`

Values: `SO_KY`, `TRUNG_KY`, `HAU_KY`, `DINH_PHONG`, `VIEN_MAN`.

| Method | Signature |
|--------|-----------|
| `values` | `static SubRealm[] values()` |
| `valueOf` | `static SubRealm valueOf(String name)` |
| `getDisplayName` | `String getDisplayName()` |
| `getEnglishName` | `String getEnglishName()` |
| `getOrder` | `int getOrder()` |
| `next` | `SubRealm next()` |
| `isMax` | `boolean isMax()` |

### `com.turtle.tutiencore.api.realm.RealmTier`

Values: `PHAM_GIOI`, `TIEN_GIOI`, `THAN_GIOI`.

| Method | Signature |
|--------|-----------|
| `values` | `static RealmTier[] values()` |
| `valueOf` | `static RealmTier valueOf(String name)` |
| `getDisplayName` | `String getDisplayName()` |
| `getEnglishName` | `String getEnglishName()` |
| `getColor` | `String getColor()` |

## Bukkit Events

### `com.turtle.tutiencore.api.event.TuViGainEvent`

Fired khi TuTienCore sắp cộng Tu Vi từ tu luyện. Cancel để chặn, hoặc đổi amount. API/command gọi `setTuVi`, `addTuVi`, `takeTuVi` trực tiếp không tự fire event này.

| Method | Signature |
|--------|-----------|
| Constructor | `public TuViGainEvent(Player player, double amount, String source)` |
| `getAmount` | `double getAmount()` |
| `setAmount` | `void setAmount(double amount)` |
| `getSource` | `String getSource()` |
| `isExternalBonusIncluded` | `boolean isExternalBonusIncluded()` |
| `setExternalBonusIncluded` | `void setExternalBonusIncluded(boolean externalBonusIncluded)` |
| `isCancelled` | `boolean isCancelled()` |
| `setCancelled` | `void setCancelled(boolean cancelled)` |
| `getHandlers` | `HandlerList getHandlers()` |
| `getHandlerList` | `static HandlerList getHandlerList()` |

### `com.turtle.tutiencore.api.event.RealmBreakthroughEvent`

Fired trước khi bắt đầu đột phá đại cảnh giới. Cancellable.

| Method | Signature |
|--------|-----------|
| Constructor | `public RealmBreakthroughEvent(Player player, Realm fromRealm, Realm toRealm)` |
| `getFromRealm` | `Realm getFromRealm()` |
| `getToRealm` | `Realm getToRealm()` |
| `isCancelled` | `boolean isCancelled()` |
| `setCancelled` | `void setCancelled(boolean cancelled)` |
| `getHandlers` | `HandlerList getHandlers()` |
| `getHandlerList` | `static HandlerList getHandlerList()` |

### `com.turtle.tutiencore.api.event.RealmBreakthroughSuccessEvent`

Fired sau khi player đột phá đại cảnh giới thành công. Không cancellable.

| Method | Signature |
|--------|-----------|
| Constructor | `public RealmBreakthroughSuccessEvent(Player player, Realm fromRealm, Realm toRealm)` |
| `getFromRealm` | `Realm getFromRealm()` |
| `getToRealm` | `Realm getToRealm()` |
| `getHandlers` | `HandlerList getHandlers()` |
| `getHandlerList` | `static HandlerList getHandlerList()` |

### `com.turtle.tutiencore.api.event.RealmBreakthroughFailEvent`

Fired khi player thất bại đột phá đại cảnh giới. Không cancellable.

| Method | Signature |
|--------|-----------|
| Constructor | `public RealmBreakthroughFailEvent(Player player, Realm currentRealm, Realm targetRealm)` |
| `getCurrentRealm` | `Realm getCurrentRealm()` |
| `getTargetRealm` | `Realm getTargetRealm()` |
| `getHandlers` | `HandlerList getHandlers()` |
| `getHandlerList` | `static HandlerList getHandlerList()` |

### `com.turtle.tutiencore.api.event.SubRealmAdvanceEvent`

Fired khi player đột phá tầng nhỏ thành công. Không cancellable.

| Method | Signature |
|--------|-----------|
| Constructor | `public SubRealmAdvanceEvent(Player player, Realm realm, SubRealm fromSubRealm, SubRealm toSubRealm)` |
| `getRealm` | `Realm getRealm()` |
| `getFromSubRealm` | `SubRealm getFromSubRealm()` |
| `getToSubRealm` | `SubRealm getToSubRealm()` |
| `getHandlers` | `HandlerList getHandlers()` |
| `getHandlerList` | `static HandlerList getHandlerList()` |

## Usage Examples

### Check cảnh giới để mở tính năng

```java
TuTienAPI api = TuTien.getApi();
UUID uuid = player.getUniqueId();

if (api != null && api.getRealmId(uuid) >= 4) {
    player.sendMessage("Bạn đã đạt Kim Đan hoặc cao hơn.");
}
```

### Lắng nghe và chỉnh Tu Vi nhận từ tu luyện

```java
@EventHandler
public void onTuViGain(TuViGainEvent event) {
    if (event.isExternalBonusIncluded()) {
        return;
    }
    if (event.getPlayer().hasPermission("vip.x2tuvi")) {
        event.setAmount(event.getAmount() * 2.0);
        event.setExternalBonusIncluded(true);
    }
}
```

### Chặn đột phá theo rule riêng

```java
@EventHandler
public void onBreakthrough(RealmBreakthroughEvent event) {
    if (!event.getPlayer().hasPermission("server.breakthrough.allowed")) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("Bạn chưa mở khóa đột phá.");
    }
}
```

### Đọc stat bonus của realm

```java
TuTienAPI api = TuTien.getApi();
Realm realm = api.getRealm(player.getUniqueId());
double attackBonus = realm.getStatBonus("ATTACK_DAMAGE");
```

## Current API Limits

- Chưa có Java API ổn định cho Nhập Thần; dùng command `/nhapthan` và config `nhapthan/infusion.yml`.
- Chưa có API save thủ công cho Tu Vi/player data; dữ liệu được flush bởi lifecycle nội bộ.
- Không có async API trong `TuTienCore-api`.

---

## 📜 License

Private — TurtleMC Server exclusive.
