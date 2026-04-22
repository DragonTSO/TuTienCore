# 🐉 SKYBLOCK TU TIÊN — Game Design Document

> **Server:** TurtleMC SkyblockDragon
> **Phiên bản:** Paper 1.21.4
> **Cập nhật:** 15/04/2026

---

## 📋 Mục Lục

1. [Tổng Quan](#1-tổng-quan)
2. [Hệ Thống Tu Luyện (Cultivation)](#2-hệ-thống-tu-luyện)
3. [Hệ Thống Skyblock Island](#3-hệ-thống-skyblock-island)
4. [Hệ Thống Dungeon](#4-hệ-thống-dungeon)
5. [Liên Kết Skyblock ↔ Dungeon](#5-liên-kết-skyblock--dungeon)
6. [Hệ Thống Vũ Khí & Trang Bị (Linh Khí)](#6-hệ-thống-vũ-khí--trang-bị)
7. [Hệ Thống Pet (Linh Thú)](#7-hệ-thống-pet)
8. [Hệ Thống Skills (Công Pháp)](#8-hệ-thống-skills)
9. [Hệ Thống Kinh Tế](#9-hệ-thống-kinh-tế)
10. [Hệ Thống Quest](#10-hệ-thống-quest)
11. [Hệ Thống Rank & Danh Hiệu](#11-hệ-thống-rank--danh-hiệu)
12. [Hệ Thống PVP — Lôi Đài](#12-hệ-thống-pvp)
13. [Hệ Thống Ruộng Linh Dược (Farm)](#13-hệ-thống-farm)
14. [Hệ Thống Sự Kiện](#14-hệ-thống-sự-kiện)
15. [Vòng Lặp Gameplay & Retention](#15-vòng-lặp-gameplay--retention)
16. [Plugin Mapping](#16-plugin-mapping)
17. [💰 Hệ Thống Nạp Tiền & VIP (Monetization)](#17-hệ-thống-nạp-tiền--vip)
18. [🧠 Chiến Lược Hút Máu Tâm Lý](#18-chiến-lược-hút-máu-tâm-lý)

> **Note:** Section 6 bao gồm hệ thống **Nhập Thần** (6.4) — hy sinh item để tăng chỉ số vĩnh viễn.

---

## 1. Tổng Quan

### Concept
**Skyblock Tu Tiên** là sự kết hợp giữa gameplay Skyblock truyền thống và thể loại **Tu Tiên** (cultivation) — nơi người chơi bắt đầu từ một hòn đảo nhỏ trên không trung, tu luyện để trở thành Tiên Nhân. Mỗi cảnh giới tu luyện mở ra nội dung mới, tạo cảm giác tiến bộ liên tục.

### Triết Lý Thiết Kế

| Nguyên Tắc | Mô Tả |
|-------------|--------|
| **Chiều Sâu** | Mỗi hệ thống có nhiều tầng nâng cấp, không bao giờ "hết đồ để cày" |
| **Chiều Rộng** | Nhiều hệ thống song song (island, dungeon, pet, skill, trang bị, farm) |
| **Liên Kết** | Skyblock cung cấp tài nguyên → Dungeon cung cấp trang bị → Cả hai đều cần cho Tu Luyện |
| **Gây Nghiện** | Daily login reward, streak bonus, giới hạn dungeon/ngày, FOMO events, gacha nhẹ |
| **Hút Máu** | VIP tầng cao, Battle Pass, Gacha, pain point → giải pháp trả phí, FOMO limited |
| **Cộng Đồng** | Island coop, dungeon party, PVP arena, giao dịch |

### Luồng Chơi Tổng Quan

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│  BẮT ĐẦU    │────▶│  SKYBLOCK    │────▶│  TU LUYỆN    │
│  (Phàm Nhân)│     │  Xây đảo,    │     │  Lên cảnh    │
│              │     │  farm, craft  │     │  giới, đột   │
│              │     │              │     │  phá boss     │
└─────────────┘     └──────┬───────┘     └──────┬───────┘
                           │                     │
                    ┌──────▼───────┐     ┌──────▼───────┐
                    │  DUNGEON     │◀───▶│  TRANG BỊ    │
                    │  PvE, Boss,  │     │  Vũ khí,     │
                    │  Phần thưởng │     │  Pet, Skill   │
                    └──────┬───────┘     └──────────────┘
                           │
                    ┌──────▼───────┐
                    │  PVP & EVENT │
                    │  Lôi Đài,    │
                    │  Rankings    │
                    └──────────────┘
```

---

## 2. Hệ Thống Tu Luyện

### 2.1 Cảnh Giới (Realm / Cultivation Stage)

Hệ thống tu luyện gồm **19 Đại Cảnh Giới**, chia thành **3 Đại Giới** (Phàm → Tiên → Thần). Mỗi cảnh giới có **5 tầng nhỏ** (Sơ Kỳ → Trung Kỳ → Hậu Kỳ → Đỉnh Phong → Viên Mãn), tạo tổng cộng **95 bậc tu luyện**.

> [!IMPORTANT]
> **Cảnh Giới dựa trên TU VI, không phải Level!**
> Người chơi tích lũy Tu Vi (tu luyện kinh nghiệm) qua mọi hoạt động. Khi đủ Tu Vi → đủ điều kiện đột phá lên cảnh giới mới.
> **Level** (1-1000) là hệ thống phụ, tăng tự động song song khi cày Tu Vi, dùng để mở khóa một số tính năng nhỏ.

#### 🟢 PHÀM GIỚI (Mortal Realm) — Cảnh Giới 1-7

| # | Cảnh Giới | Tu Vi YC | Thực Lực YC | Dungeon Mở Khóa | Đặc Quyền |
|---|-----------|----------|-------------|-----------------|-----------|
| 1 | **Phàm Nhân** (Mortal) | 0 | 0 | — | Island cơ bản, Farm |
| 2 | **Luyện Khí** (Qi Refinement) | 5,000 | 500 | Yêu Thú Lâm | Pet slot 1, Chọn Class |
| 3 | **Trúc Cơ** (Foundation) | 25,000 | 2,000 | Âm Hồn Động | Skill slot 2, Trade |
| 4 | **Kim Đan** (Golden Core) | 100,000 | 8,000 | Hỏa Diễm Sơn | Pet slot 2, Island T3 |
| 5 | **Nguyên Anh** (Nascent Soul) | 350,000 | 25,000 | Băng Phong Cốc | Skill slot 3, PVP |
| 6 | **Hóa Thần** (Spirit Severing) | 1,000,000 | 80,000 | Huyết Nguyệt Đàn | Island T5, Pet slot 3 |
| 7 | **Luyện Hư** (Void Refining) | 3,000,000 | 200,000 | Lôi Đình Tháp | Legendary craft |

#### 🔵 TIÊN GIỚI (Immortal Realm) — Cảnh Giới 8-13

| # | Cảnh Giới | Tu Vi YC | Thực Lực YC | Dungeon Mở Khóa | Đặc Quyền |
|---|-----------|----------|-------------|-----------------|-----------|
| 8 | **Đại Thừa** (Mahayana) | 8,000,000 | 500,000 | Vạn Độc Trì | Island T6 |
| 9 | **Độ Kiếp** (Tribulation) | 20,000,000 | 800,000 | Long Cốt Mộ | Chuyển Class lần 1 |
| 10 | **Tiên Nhân** (Immortal) | 50,000,000 | 1,500,000 | Tiên Phế Đô | Danh hiệu Tiên |
| 11 | **Thiên Tiên** (Heavenly Immortal) | 120,000,000 | 3,000,000 | Hỗn Nguyên Giới | Class Skill T2 |
| 12 | **Kim Tiên** (Golden Immortal) | 300,000,000 | 6,000,000 | Bí Cảnh Cấp 1 | Pet slot 4 |
| 13 | **Thái Ất** (Supreme Unity) | 700,000,000 | 12,000,000 | Bí Cảnh Cấp 2 | Chuyển Class lần 2 |

#### 🟣 THẦN GIỚI (Divine Realm) — Cảnh Giới 14-19

| # | Cảnh Giới | Tu Vi YC | Thực Lực YC | Dungeon Mở Khóa | Đặc Quyền |
|---|-----------|----------|-------------|-----------------|-----------|
| 14 | **Đại La** (Great Luo) | 1,500,000,000 | 25,000,000 | Thiên Đạo Thí Luyện | Island T7 |
| 15 | **Chuẩn Thánh** (Quasi-Saint) | 3,500,000,000 | 50,000,000 | Bí Cảnh Cấp 3 | Class Skill T3 |
| 16 | **Thánh Nhân** (Saint) | 8,000,000,000 | 100,000,000 | Thánh Giới Dungeon | Pet slot 5 |
| 17 | **Thiên Đế** (Heavenly Emperor) | 20,000,000,000 | 250,000,000 | Đế Cung | Danh hiệu Đế |
| 18 | **Đạo Tổ** (Dao Ancestor) | 50,000,000,000 | 600,000,000 | Đạo Cảnh | Toàn bộ skill max |
| 19 | **Hồng Mông** (Primordial Chaos) | 100,000,000,000 | 1,000,000,000 | Tất cả | Danh hiệu tối thượng |

#### Hệ Thống Tầng Nhỏ (5 Bậc Mỗi Cảnh Giới)

> [!IMPORTANT]
> Mỗi Đại Cảnh Giới có **5 tầng nhỏ**: **Sơ Kỳ → Trung Kỳ → Hậu Kỳ → Đỉnh Phong → Viên Mãn**.
> Tầng nhỏ là các **mốc Tu Vi trung gian** trong khoảng Tu Vi của cảnh giới đó.
> Khi đạt **Viên Mãn** và tích đủ Tu Vi cảnh giới tiếp → sẵn sàng **Đột Phá** lên Đại Cảnh Giới mới.
>
> **Ví dụ:** Luyện Khí bắt đầu ở 5K Tu Vi → Sơ Kỳ (5K), Trung Kỳ (8K), Hậu Kỳ (12K), Đỉnh Phong (18K), Viên Mãn (23K) → Đủ 25K = đột phá Trúc Cơ.

##### 🟢 PHÀM GIỚI — Tầng Nhỏ Chi Tiết

| # | Cảnh Giới | Sơ Kỳ | Trung Kỳ | Hậu Kỳ | Đỉnh Phong | Viên Mãn | Đột Phá → |
|---|-----------|--------|----------|---------|------------|----------|-----------|
| 1 | **Phàm Nhân** | 0 | 500 | 1,200 | 2,500 | 4,000 | 5,000 → Luyện Khí |
| 2 | **Luyện Khí** | 5,000 | 8,000 | 12,000 | 18,000 | 23,000 | 25,000 → Trúc Cơ |
| 3 | **Trúc Cơ** | 25,000 | 35,000 | 50,000 | 75,000 | 92,000 | 100,000 → Kim Đan |
| 4 | **Kim Đan** | 100,000 | 135,000 | 185,000 | 260,000 | 325,000 | 350,000 → Nguyên Anh |
| 5 | **Nguyên Anh** | 350,000 | 450,000 | 580,000 | 780,000 | 930,000 | 1,000,000 → Hóa Thần |
| 6 | **Hóa Thần** | 1,000,000 | 1,300,000 | 1,700,000 | 2,300,000 | 2,800,000 | 3,000,000 → Luyện Hư |
| 7 | **Luyện Hư** | 3,000,000 | 3,800,000 | 4,800,000 | 6,200,000 | 7,500,000 | 8,000,000 → Đại Thừa |

##### 🔵 TIÊN GIỚI — Tầng Nhỏ Chi Tiết

| # | Cảnh Giới | Sơ Kỳ | Trung Kỳ | Hậu Kỳ | Đỉnh Phong | Viên Mãn | Đột Phá → |
|---|-----------|--------|----------|---------|------------|----------|-----------|
| 8 | **Đại Thừa** | 8,000,000 | 10,000,000 | 13,000,000 | 16,500,000 | 19,000,000 | 20,000,000 → Độ Kiếp |
| 9 | **Độ Kiếp** | 20,000,000 | 25,000,000 | 32,000,000 | 40,000,000 | 47,000,000 | 50,000,000 → Tiên Nhân |
| 10 | **Tiên Nhân** | 50,000,000 | 62,000,000 | 78,000,000 | 100,000,000 | 115,000,000 | 120,000,000 → Thiên Tiên |
| 11 | **Thiên Tiên** | 120,000,000 | 150,000,000 | 190,000,000 | 245,000,000 | 285,000,000 | 300,000,000 → Kim Tiên |
| 12 | **Kim Tiên** | 300,000,000 | 370,000,000 | 460,000,000 | 570,000,000 | 660,000,000 | 700,000,000 → Thái Ất |
| 13 | **Thái Ất** | 700,000,000 | 840,000,000 | 1,000,000,000 | 1,250,000,000 | 1,430,000,000 | 1,500,000,000 → Đại La |

##### 🟣 THẦN GIỚI — Tầng Nhỏ Chi Tiết

| # | Cảnh Giới | Sơ Kỳ | Trung Kỳ | Hậu Kỳ | Đỉnh Phong | Viên Mãn | Đột Phá → |
|---|-----------|--------|----------|---------|------------|----------|-----------|
| 14 | **Đại La** | 1,500,000,000 | 1,850,000,000 | 2,300,000,000 | 2,900,000,000 | 3,350,000,000 | 3,500,000,000 → Chuẩn Thánh |
| 15 | **Chuẩn Thánh** | 3,500,000,000 | 4,300,000,000 | 5,400,000,000 | 6,700,000,000 | 7,600,000,000 | 8,000,000,000 → Thánh Nhân |
| 16 | **Thánh Nhân** | 8,000,000,000 | 10,000,000,000 | 13,000,000,000 | 16,500,000,000 | 19,000,000,000 | 20,000,000,000 → Thiên Đế |
| 17 | **Thiên Đế** | 20,000,000,000 | 25,000,000,000 | 32,000,000,000 | 42,000,000,000 | 48,000,000,000 | 50,000,000,000 → Đạo Tổ |
| 18 | **Đạo Tổ** | 50,000,000,000 | 60,000,000,000 | 72,000,000,000 | 88,000,000,000 | 96,000,000,000 | 100,000,000,000 → Hồng Mông |
| 19 | **Hồng Mông** | 100,000,000,000 | 130,000,000,000 | 180,000,000,000 | 280,000,000,000 | 450,000,000,000 | ∞ (Cực Đỉnh) |

> [!NOTE]
> **Quy luật tầng nhỏ:**
> - Khoảng cách Tu Vi giữa các tầng **tăng dần** (Sơ Kỳ → Trung Kỳ dễ, Đỉnh Phong → Viên Mãn khó nhất)
> - Tầng nhỏ **ảnh hưởng đến yêu cầu Dungeon** — một số Dungeon yêu cầu cảnh giới + tầng nhỏ cụ thể (VD: Dungeon T2 cần Luyện Khí **Hậu Kỳ**)
> - Mỗi lần lên tầng nhỏ → hiệu ứng **"Đột Phá Tiểu Cảnh Giới"** (animation nhẹ + thông báo chat)
> - Tầng nhỏ hiển thị trên thanh thông tin: `[Luyện Khí — Đỉnh Phong]`
> - **Hồng Mông** (CG 19) không có đột phá tiếp — các tầng nhỏ mở rộng vô hạn để endgame player tiếp tục cày

### 2.2 Hệ Thống Class (Nghề Tu Luyện)

Khi đạt **Luyện Khí** (Cảnh Giới 2), người chơi chọn **1 trong 5 Class**:

| Class | Tên | Vai Trò | Vũ Khí Chính | Stat Chính |
|-------|-----|---------|-------------|-----------|
| ⚔️ | **Kiếm Tu** (Sword Cultivator) | Melee DPS | Kiếm, Đao | ATK, Crit |
| 🔮 | **Pháp Tu** (Spell Cultivator) | Magic DPS / CC | Pháp Bảo, Staff | Skill DMG, SPD |
| 🛡️ | **Thể Tu** (Body Cultivator) | Tank / Brawler | Quyền, Thương | DEF, HP |
| 💚 | **Y Tu** (Healer Cultivator) | Support / Healer | Phù, Trượng | Heal, Buff |
| 🗡️ | **Ám Tu** (Shadow Cultivator) | Assassin / Burst | Đoản Đao, Ám Khí | Crit, SPD |

#### Class Skills (Mỗi class có 5 skill riêng)

**⚔️ Kiếm Tu — `Sát thương bùng nổ, combo nhanh`**

| # | Skill | Hiệu Ứng | CD | Unlock |
|---|-------|-----------|----|--------|
| 1 | Phong Kiếm Trảm | 150 DMG, 3 combo liên tiếp | 5s | CG 2 |
| 2 | Kiếm Khí Phóng | 300 DMG ranged, xuyên mob | 8s | CG 3 |
| 3 | Vạn Kiếm Quy Tông | 800 DMG AoE, xuyên giáp 30% | 15s | CG 5 |
| 4 | Kiếm Khí Tung Hoành | 2,000 DMG cone, crit +50% 5s | 30s | CG 8 |
| 5 | **Nhất Kiếm Phá Thiên** (Ult) | 10,000 DMG single target, ignore DEF | 120s | CG 10 |

**🔮 Pháp Tu — `AoE damage, crowd control`**

| # | Skill | Hiệu Ứng | CD | Unlock |
|---|-------|-----------|----|--------|
| 1 | Hỏa Cầu Thuật | 120 DMG AoE 3 block | 6s | CG 2 |
| 2 | Băng Phong Bão | 400 DMG AoE + Freeze 2s | 12s | CG 3 |
| 3 | Lôi Đình Vạn Quân | 1,200 DMG AoE rộng + Stun 3s | 20s | CG 5 |
| 4 | Ngũ Hành Luân Chuyển | 1,800 DMG + random debuff (burn/freeze/stun) | 25s | CG 8 |
| 5 | **Thiên Địa Pháp Tướng** (Ult) | Transform 15s: +200% Skill DMG, AoE x2 | 150s | CG 10 |

**🛡️ Thể Tu — `Tanky, taunt, phản damage`**

| # | Skill | Hiệu Ứng | CD | Unlock |
|---|-------|-----------|----|--------|
| 1 | Thạch Bì Thuật | +50% DEF 8s, giảm DMG nhận 20% | 10s | CG 2 |
| 2 | Bá Vương Quyền | 400 DMG + Knockback + Taunt 3s | 12s | CG 3 |
| 3 | Thiết Bích | Shield 500HP + phản 20% DMG 6s | 18s | CG 5 |
| 4 | Bất Động Minh Vương | +100% DEF, phản 40% DMG 6s, AoE Taunt | 25s | CG 8 |
| 5 | **Kim Cang Bất Hoại** (Ult) | Bất tử 8s + phản 50% DMG + Taunt toàn bộ | 180s | CG 10 |

**💚 Y Tu — `Heal, buff team, cleanse`**

| # | Skill | Hiệu Ứng | CD | Unlock |
|---|-------|-----------|----|--------|
| 1 | Linh Dược Thuật | Heal 200 HP cho 1 target | 6s | CG 2 |
| 2 | Sinh Cơ Quyết | HoT 50HP/s 10s cho cả party | 15s | CG 3 |
| 3 | Linh Khí Hộ Thể | Shield 400HP + ATK +15% toàn party 8s | 18s | CG 5 |
| 4 | Tịnh Hóa | Cleanse debuff + Shield 800HP toàn party | 20s | CG 8 |
| 5 | **Luân Hồi Mộc** (Ult) | Rez tất cả ally + Full heal party + Immune 3s | 240s | CG 10 |

**🗡️ Ám Tu — `Burst damage, stealth, critical`**

| # | Skill | Hiệu Ứng | CD | Unlock |
|---|-------|-----------|----|--------|
| 1 | Ám Ảnh Bộ | Tàng hình 5s, đòn tiếp theo +100% DMG | 10s | CG 2 |
| 2 | Độc Nhẫn | 250 DMG + Poison 5s (50 DMG/s) | 8s | CG 3 |
| 3 | Ám Sát | Teleport sau lưng target + 1,500 DMG (x2 từ tàng hình) | 15s | CG 5 |
| 4 | Vạn Ám Thiên La | 2,000 DMG AoE + Blind 3s + Slow 50% | 25s | CG 8 |
| 5 | **Tử Thần Giáng Lâm** (Ult) | Mark target 10s: mọi đòn crit + 8,000 bonus DMG cuối | 150s | CG 10 |

#### Quy Tắc Class

| Quy Tắc | Chi Tiết |
|----------|---------|
| **Chọn Class** | Tại Cảnh Giới 2 (Luyện Khí), chọn qua NPC `/class` |
| **Chuyển Class** | Được chuyển tại CG 9 (Độ Kiếp) và CG 13 (Thái Ất), tốn 500 LN |
| **Class Passive** | Mỗi class có passive riêng tăng theo cảnh giới |
| **Party Bonus** | Party 5 người đủ 5 class = +20% toàn bộ stat (Ngũ Hành bonus) |
| **Class Gear** | Một số gear chỉ dùng được bởi class cụ thể |
| **Tương Khắc** | Kiếm > Y > Ám > Pháp > Thể > Kiếm (vòng tương khắc) |
### 2.2 Tu Vi & Level

#### Tu Vi (Tu Luyện Kinh Nghiệm) — Hệ Thống CHÍNH

Tu Vi là **đại lượng tích lũy vĩnh viễn**, KHÔNG bao giờ mất. Mọi hoạt động đều tạo Tu Vi:

| Hoạt Động | Tu Vi/Lần | Giới Hạn |
|-----------|-----------|----------|
| Nhận **Linh Quặng** (mine ore trên đảo) | 5-50 /quặng | Không giới hạn |
| Thu hoạch **Linh Dược** (% drop khi crop cây vanilla) | 2-25 /lần drop | Không giới hạn |
| Hoàn thành Dungeon | 200-5,000 | **Không giới hạn** |
| Giết Boss Dungeon | 500-10,000 | **Không giới hạn** |
| Quest hàng ngày | 100-500 | 5 quest/ngày |
| Quest tuần | 1,000-3,000 | 3 quest/tuần |
| **Tu Tập** (/tutap tại khu Linh Mạch) | 20-200/phút | Không giới hạn |
| PVP thắng | 50-200 | 20 trận/ngày |
| Nhập Thần (bonus Tu Vi) | 50-500 | Theo item nhập |

> Tu Vi tích lũy liên tục → khi đạt mốc cảnh giới → thanh Tu Vi **phát sáng** → có thể đột phá

**📦 Linh Quặng — Drop từ Mine Ore trên Đảo:**

Khi đào ore từ cobble generator / ore generator trên đảo, có **% rơi ra Linh Quặng**:

| Ore Đào | % Drop Linh Quặng | Loại Quặng | Tu Vi Nhận |
|---------|-------------------|------------|------------|
| Cobblestone | 2% | Phàm Quặng | +5 |
| Coal Ore | 5% | Phàm Quặng | +5 |
| Iron Ore | 8% | Hạ Quặng | +10 |
| Gold Ore | 12% | Trung Quặng | +20 |
| Diamond Ore | 20% | Thượng Quặng | +35 |
| Emerald Ore | 25% | Cực Quặng | +50 |
| Ancient Debris | 35% | Tiên Quặng | +80 |

> Ore tier từ generator tăng theo **Island Tier** (xem section 3)
> VIP có bonus % drop Linh Quặng (+5% đến +40%)

**🌿 Linh Dược — Thu Hoạch Cây Farm trên Đảo:**

Sử dụng **cây vanilla bình thường** trên đảo. Khi thu hoạch crop có **% tỉ lệ rớt Linh Dược** (thấp hơn mine ore):

| Cây Vanilla | % Drop Linh Dược | Tu Vi Nhận | Ghi Chú |
|-------------|-------------------|------------|---------|
| Wheat | 3% | +3 | Dễ farm, số lượng bù |
| Carrot | 4% | +5 | Dễ farm |
| Potato | 4% | +5 | Dễ farm |
| Beetroot | 6% | +8 | Drop rate cao hơn |
| Melon (slice) | 2% | +2 | Mỗi slice tính riêng |
| Pumpkin | 8% | +10 | Auto-farm friendly |
| Sugar Cane | 3% | +4 | Cao tầng, auto-farm |
| Nether Wart | 10% | +15 | Khó farm hơn |
| Chorus Fruit | 15% | +25 | End game crop |
| Cocoa Beans | 5% | +6 | Trung bình |

> **Farm cho Tu Vi ít hơn Mine ore** — Mine ore là nguồn Tu Vi chính từ đảo
> Linh Dược dùng để: craft Đan Dược (buff), craft Pet Food, craft nguyên liệu Cường Hóa
> VIP bonus +3% đến +15% drop Linh Dược

**🧘 Tu Tập — Hấp Thụ Linh Mạch (AFK Tu Vi):**

Người chơi đến **khu vực Linh Mạch** (warp) và sử dụng lệnh `/tutap` để ngồi thiền, hấp thụ linh khí từ Linh Mạch để tăng Tu Vi.

| Khu Linh Mạch | Vị Trí | Tu Vi/Phút | Cảnh Giới YC | Đặc Biệt |
|---------------|--------|------------|-------------|-----------|
| **Phàm Giới Linh Mạch** | /warp tutap1 | 20/phút | Phàm Nhân | Miễn phí |
| **Thanh Phong Linh Mạch** | /warp tutap2 | 50/phút | Luyện Khí | 50 LS/giờ |
| **Vân Hải Linh Mạch** | /warp tutap3 | 100/phút | Kim Đan | 200 LS/giờ |
| **Thiên Đỉnh Linh Mạch** | /warp tutap4 | 200/phút | Hóa Thần | 500 LS/giờ |
| **VIP Linh Mạch** | /warp tutapvip | 150/phút | VIP 3+ | Miễn phí, x1.5 bonus |

**Cơ chế `/tutap`:**
- Gõ `/tutap` tại khu Linh Mạch → Nhân vật **ngồi thiền** (animation)
- Tu Vi tự động cộng mỗi phút khi online tại khu vực
- **Phải ở trong khu vực** — ra khỏi vùng = ngừng nhận Tu Vi
- **Không cần thao tác** — AFK thoải mái, nhưng phải online
- **Combo với VIP**: VIP nhận thêm bonus Tu Vi khi tu tập (+10% đến +50%)
- **Combo với Pet**: Mang pet Hỗ Trợ → +20% Tu Vi khi tu tập

> **Tâm lý:** Người chơi bận có thể AFK tu tập để không bị tụt lại
> Khu cao cấp tốn Linh Thạch → tiêu hao currency → VIP miễn phí → hút máu

#### Level (1-1000) — Hệ Thống PHỤ

Level tăng **tự động** song song khi cày Tu Vi (cứ X Tu Vi = +1 Level). Level dùng để:
- Hiển thị tiến độ nhanh (dễ hiểu hơn số Tu Vi lớn)
- Mở khóa một số quest, NPC dialogue
- Đóng góp vào Thực Lực (Level × 5)
- **KHÔNG** quyết định cảnh giới — chỉ Tu Vi mới quyết định

### 2.3 Đột Phá Cảnh Giới (Breakthrough) — Thiên Lôi Kiếp ⚡

Khi đủ điều kiện, người chơi kích hoạt đột phá → trời giáng **Thiên Lôi Kiếp** (sét) xuống người chơi. Sống sót qua Kiếp Lôi = đột phá thành công!

#### 2.3.1 Đột Phá Đại Cảnh Giới (Lên cảnh giới mới)

> **Điều kiện đột phá Đại Cảnh Giới:**
> 1. ✅ Tích đủ **Tu Vi** yêu cầu (đạt mốc Viên Mãn + đủ Tu Vi cảnh giới tiếp)
> 2. ✅ Đạt **Thực Lực** tối thiểu
> 3. ✅ Thu thập **Đột Phá Đan** (vật phẩm drop từ Dungeon hoặc craft)
> 4. ✅ Vượt qua **Thiên Lôi Kiếp** (số tia sét tăng theo cảnh giới)

**⚡ Cơ chế Thiên Lôi Kiếp (Đại Cảnh Giới):**

```
┌─────────────────────────────────────────────────────────────┐
│                ⚡ THIÊN LÔI KIẾP ⚡                          │
│                                                             │
│   Kích hoạt đột phá → Trời tối, sấm rền                   │
│   → Tia sét lần lượt giáng xuống người chơi                │
│   → Số tia sét & DMG tăng theo cảnh giới cao hơn           │
│   → Giữa mỗi tia sét: 2-3 giây nghỉ                       │
│                                                             │
│   Phàm Giới:  ⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡ (10 tia, nhẹ)                │
│   Tiên Giới:  ⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡ (18 tia, mạnh)    │
│   Thần Giới:  ⚡⚡⚡⚡⚡⚡⚡...⚡⚡⚡⚡⚡⚡⚡ (36 tia, hủy diệt!) │
│                                                             │
│   ✅ Sống sót hết tia sét → Đột phá THÀNH CÔNG             │
│   💀 Chết giữa chừng     → Đột phá THẤT BẠI               │
│                                                             │
│   ⚠️ Có TỈ LỆ THÀNH CÔNG — nếu fail roll                  │
│      → tia sét gây x2 DMG → gần như chắc chắn chết         │
└─────────────────────────────────────────────────────────────┘
```

**Thiên Lôi Kiếp chi tiết từng Cảnh Giới:**

| # | Đột Phá Lên | Số Tia Sét | DMG / Tia | Tổng DMG (Roll OK) | DMG Fail Roll (x2) | Tỉ Lệ |
|---|-------------|-----------|-----------|--------------------|--------------------|--------|
| 2 | **Luyện Khí** | 10 tia | 3 ❤ | 30 ❤ | 60 ❤ | 95% |
| 3 | **Trúc Cơ** | 10 tia | 3 ❤ | 30 ❤ | 60 ❤ | 95% |
| 4 | **Kim Đan** | 12 tia | 3 ❤ | 36 ❤ | 72 ❤ | 90% |
| 5 | **Nguyên Anh** | 12 tia | 3.5 ❤ | 42 ❤ | 84 ❤ | 85% |
| 6 | **Hóa Thần** | 14 tia | 4 ❤ | 56 ❤ | 112 ❤ | 80% |
| 7 | **Luyện Hư** | 14 tia | 4 ❤ | 56 ❤ | 112 ❤ | 75% |
| 8 | **Đại Thừa** | 18 tia | 4.5 ❤ | 81 ❤ | 162 ❤ | 70% |
| 9 | **Độ Kiếp** | 18 tia | 5 ❤ | 90 ❤ | 180 ❤ | 65% |
| 10 | **Tiên Nhân** | 21 tia | 5 ❤ | 105 ❤ | 210 ❤ | 60% |
| 11 | **Thiên Tiên** | 21 tia | 5.5 ❤ | 115.5 ❤ | 231 ❤ | 55% |
| 12 | **Kim Tiên** | 24 tia | 6 ❤ | 144 ❤ | 288 ❤ | 50% |
| 13 | **Thái Ất** | 24 tia | 6 ❤ | 144 ❤ | 288 ❤ | 45% |
| 14 | **Đại La** | 27 tia | 6.5 ❤ | 175.5 ❤ | 351 ❤ | 40% |
| 15 | **Chuẩn Thánh** | 27 tia | 7 ❤ | 189 ❤ | 378 ❤ | 35% |
| 16 | **Thánh Nhân** | 30 tia | 7 ❤ | 210 ❤ | 420 ❤ | 30% |
| 17 | **Thiên Đế** | 30 tia | 7.5 ❤ | 225 ❤ | 450 ❤ | 28% |
| 18 | **Đạo Tổ** | 33 tia | 8 ❤ | 264 ❤ | 528 ❤ | 25% |
| 19 | **Hồng Mông** | 36 tia | 8 ❤ | 288 ❤ | 576 ❤ | 20% |

> [!IMPORTANT]
> **Cách hoạt động tỉ lệ:**
> - Khi kích hoạt đột phá, hệ thống **roll tỉ lệ** ngay lập tức
> - **Roll thành công** → tia sét gây DMG bình thường (cột "DMG / Tia") → chuẩn bị gear tốt là sống
> - **Roll thất bại** → tia sét gây **x2 DMG** → tổng DMG khổng lồ → gần như chắc chắn chết
> - Người chơi **KHÔNG biết** roll thành công hay thất bại → phải buff, potion, gear DEF để đề phòng
> - **Cảnh giới càng cao → cần max HP & DEF càng lớn** mới chịu nổi Kiếp Lôi dù roll thành công
> - Ví dụ: Đột phá Hồng Mông cần sống sót **288 ❤ tổng DMG** (36 tia × 8 ❤) → cần hàng trăm HP + giáp Thần Phẩm!

**Hiệu ứng Thiên Lôi Kiếp:**
- Trời chuyển tối (thời tiết thunder) trong bán kính 50 block
- Sét đánh có particle effect đặc biệt (sét màu tím/vàng tùy đại giới)
- Sound effect tiếng sấm rền
- Người chơi xung quanh thấy được sét → tạo **event cộng đồng**
- Broadcast chat: `⚡ [Tên] đang vượt Thiên Lôi Kiếp để đột phá [Cảnh Giới]!`
- Thành công: `✨ [Tên] đã vượt Kiếp Lôi, đột phá thành công [Cảnh Giới]!`
- Thất bại: `💀 [Tên] không chống nổi Thiên Lôi, đột phá thất bại...`

```
   TU VI █████████████████████████ 100% → ✅ Sẵn sàng đột phá!
   
   Điều kiện:
   ✅ Thực Lực: 8,500 / 8,000 (đủ)
   ✅ Đột Phá Đan: 3/3 (đủ)
   ✅ Tu Vi: Viên Mãn (đủ)
   
   ⚡ [BẮT ĐẦU THIÊN LÔI KIẾP] ⚡
   → Chuẩn bị: Uống potion, mang gear tốt, buff HP!
```

#### 2.3.2 Đột Phá Tầng Nhỏ (Tiểu Cảnh Giới)

Khi Tu Vi đạt mốc tầng nhỏ tiếp theo (VD: Sơ Kỳ → Trung Kỳ), người chơi tự động hoặc kích hoạt đột phá tầng nhỏ:

**⚡ Cơ chế Tiểu Lôi Kiếp (Tầng Nhỏ):**

| Tầng Nhỏ | Số Tia Sét | DMG / Tia | Tổng DMG | Tỉ Lệ Thành Công |
|----------|-----------|-----------|----------|-------------------|
| Sơ Kỳ → Trung Kỳ | 3 tia | 1 ❤ (2 HP) | 3 ❤ | **100%** |
| Trung Kỳ → Hậu Kỳ | 3 tia | 1.5 ❤ (3 HP) | 4.5 ❤ | **100%** |
| Hậu Kỳ → Đỉnh Phong | 4 tia | 1.5 ❤ (3 HP) | 6 ❤ | **100%** |
| Đỉnh Phong → Viên Mãn | 5 tia | 2 ❤ (4 HP) | 10 ❤ | **100%** |

> [!NOTE]
> **Đột phá tầng nhỏ luôn 100% thành công** — chỉ cần không chết là được.
> Với HP mặc định 20 (10 ❤), hầu hết người chơi sẽ sống dễ dàng.
> Đây là **trải nghiệm thị giác** hơn là thử thách thực sự — tạo cảm giác "tu luyện" mỗi lần lên tầng.

**So sánh Đại Cảnh Giới vs Tầng Nhỏ:**

| | Đại Cảnh Giới | Tầng Nhỏ |
|---|---------------|----------|
| **Số tia sét** | 10 tia | 3-5 tia |
| **DMG / tia** | 3 ❤ (hoặc 6 ❤ nếu fail roll) | 1-2 ❤ |
| **Tỉ lệ thành công** | 25% - 95% (tùy CG) | **100%** |
| **Có thể chết?** | ✅ Rất có thể | ❌ Gần như không |
| **Broadcast chat** | ✅ Toàn server | 💬 Chỉ gần 30 block |
| **Yêu cầu Đột Phá Đan** | ✅ Cần | ❌ Không cần |
| **Cooldown thất bại** | 1 giờ | Không có |
| **Hiệu ứng** | Trời tối, sấm lớn | Sét nhẹ, particle nhỏ |

#### 2.3.3 Thất Bại Đột Phá Đại Cảnh Giới

Khi **chết** trong Thiên Lôi Kiếp (Đại Cảnh Giới):
- 💀 Người chơi **hồi sinh** tại spawn point
- Mất **50% Đột Phá Đan** đã sử dụng
- Cooldown **1 giờ** trước khi thử lại
- **KHÔNG** mất Tu Vi — Tu Vi vẫn giữ nguyên
- **KHÔNG** mất trang bị hay Linh Thạch
- Tu Vi vẫn ở mốc Viên Mãn, chỉ cần thu thập lại Đan và thử lại

> [!TIP]
> **Chiến thuật vượt Thiên Lôi Kiếp:**
> - 🧪 Uống **Hồi Phục Đan** (heal pot) trước khi bắt đầu → tăng max HP
> - 🛡️ Mặc **giáp DEF cao** → giảm DMG từ sét
> - 💚 Mang **Pet Y Tu** → tự heal giữa các tia sét
> - 🔮 Sử dụng **Hộ Thể Phù** (talisman) → chặn 1-2 tia sét
> - 💎 VIP có **Thiên Lôi Hộ Phù** → giảm 20% DMG sét (shop VIP)
> - 👥 **Không thể nhờ người khác heal** — đây là solo tribulation!

### 2.4 Hệ Thống Thực Lực (戰力 — Combat Power)

**Thực Lực** là chỉ số tổng hợp thể hiện **sức mạnh toàn diện** của người chơi. Con số này được tính tự động từ tất cả hệ thống và hiển thị luôn dưới tên nhân vật.

#### Công Thức Tính Thực Lực

```
THỰC LỰC = Gear_Score + Nhập_Thần_Score + Pet_Score + Cảnh_Giới_Bonus + (Tu_Vi ÷ 200)
```

| Thành Phần | Cách Tính | Ví Dụ |
|------------|-----------|-------|
| **Gear_Score** | Tổng điểm từ set giáp + vũ khí đang mặc (tier x cường hóa) | Cực Phẩm Set +10 = ~15,000 |
| **Nhập_Thần_Score** | Tổng chỉ số đã nhập thần (ATK + DEF + HP + SPD + CRIT + LUCK) | 500 ATK + 300 DEF = ~800 |
| **Pet_Score** | Tổng điểm từ tất cả pet đang mang (rarity x level) | Legendary Lv100 = ~5,000 |
| **Cảnh_Giới_Bonus** | Bonus cố định khi đột phá cảnh giới | Tiên Nhân = +50,000 |
| **Tu_Vi ÷ 200** | Tu Vi tích lũy chia 1000 | 50M Tu Vi = +250,000 |

> **Ví dụ tính:** Gear 15K + Nhập Thần 800 + Pet 5K + Cảnh Giới (Kim Đan) 3K + Tu Vi (100K/200) 500 = **Thực Lực: 24,300**
#### Hiển Thị Thực Lực

| Vị Trí | Cách Hiển Thị |
|---------|----------------|
| **Dưới tên** | `⚔ Thực Lực: 125,000` (màu theo rank) |
| **Tab list** | Cột Thực Lực bên cạnh tên |
| **Menu Info** | Trang thông tin nhân vật (`/info`) |
| **Bảng Xếp Hạng** | Top Thực Lực toàn server |
| **Khi nhắm** | Xem Thực Lực người khác khi nhấp chuột |

#### Màu Sắc Theo Mức Thực Lực

| Thực Lực | Màu | Xếp Hạng |
|----------|-----|----------|
| 0 - 1,000 | &7 (Xám) | Phàm Nhân |
| 1,001 - 5,000 | &a (Xanh lá) | Sơ Cấp |
| 5,001 - 20,000 | &b (Xanh dương) | Trung Cấp |
| 20,001 - 80,000 | &5 (Tím) | Cao Cấp |
| 80,001 - 250,000 | &6 (Vàng) | Cường Giả |
| 250,001 - 600,000 | &c (Đỏ) | Bá Chủ |
| 600,001+ | &c&l (Đỏ đậm) | Thiên Huyền |

> **Tâm lý:** Thực Lực là **con số flex lớn nhất** — người chơi luôn so sánh với nhau
> Mỗi hành động (cày dungeon, nhập thần, lên pet, cường hóa) đều tăng số này → Tất cả đều có ý nghĩa
> **Dungeon yêu cầu Cảnh Giới + Thực Lực** → Người chơi PHẢI tu luyện đủ Tu Vi VÀ đủ mạnh mới vào được

---

## 3. Hệ Thống Skyblock Island

### 3.1 Island Tier

Sử dụng **SuperiorSkyblock2** làm nền tảng, mở rộng với hệ thống Tier:

| Tier | Tên Đảo | Kích Thước | Yêu Cầu Cảnh Giới | Tính Năng |
|------|---------|-----------|-------------------|-----------|
| 1 | **Hoang Đảo** | 50x50 | Phàm Nhân | Cobblestone gen, 1 farm plot |
| 2 | **Linh Đảo** | 100x100 | Luyện Khí | Ore gen (sắt, vàng), 3 farm plot, 1 spawner slot |
| 3 | **Tiên Đảo** | 150x150 | Kim Đan | Diamond/Emerald gen, 6 farm plot, 3 spawner slot |
| 4 | **Long Đảo** | 200x200 | Nguyên Anh | Netherite gen, 10 farm plot, 5 spawner slot, Warp point |
| 5 | **Thần Đảo** | 300x300 | Hóa Thần | Custom ore gen, 15 farm plot, 8 spawner slot |
| 6 | **Thiên Đảo** | 500x500 | Đại Thừa | Unlimited plots, 15 spawner slot, Private dungeon portal |

### 3.2 Island Upgrades

Ngoài Tier, người chơi nâng cấp từng khía cạnh của đảo bằng **Linh Thạch** (tiền tệ chính):

| Upgrade | Max Level | Hiệu Ứng | Chi Phí (mỗi level) |
|---------|-----------|-----------|---------------------|
| **Linh Mạch** (Ore Gen Speed) | 20 | +5% tốc độ gen ore | 500 → 50,000 |
| **Linh Khí Nồng Độ** (AFK EXP Bonus) | 15 | +2% Tu Vi AFK | 1,000 → 30,000 |
| **Linh Điền** (Farm Slot) | 20 | +1 farm plot | 300 → 20,000 |
| **Trận Pháp** (Island Border Defense) | 10 | +10% damage reduction trên đảo | 2,000 → 100,000 |
| **Kho Báu** (Island Storage) | 10 | +1 row kho đảo | 1,500 → 40,000 |
| **Spawner Mở Rộng** | 15 | +1 spawner slot | 5,000 → 80,000 |
| **Truyền Tống Trận** (Warp Points) | 5 | +1 warp point | 10,000 → 100,000 |

### 3.3 Island Members & Roles

| Role | Quyền Hạn |
|------|-----------|
| **Đảo Chủ** (Owner) | Toàn quyền, kick, upgrade, set role |
| **Trưởng Lão** (Elder) | Build, break, invite, trade, dùng spawner |
| **Đệ Tử** (Member) | Build, break, farm, dùng spawner |
| **Khách** (Visitor) | Xem, không tương tác |

**Lợi ích chơi cùng nhau:**
- Island level = tổng đóng góp tất cả thành viên → Ranking đảo
- Thành viên đảo chia sẻ **Linh Mạch Buff** (AFK bonus)
- Party dungeon cùng thành viên đảo → +20% drop bonus
- Hệ thống **Đóng Góp Đảo**: thành viên farm/craft → nhận điểm đóng góp → đổi thưởng

### 3.4 Cobblestone/Ore Generator Progression

Generator nâng cấp theo Island Tier:

```
Tier 1: Cobblestone (60%), Coal (25%), Iron (10%), Gold (5%)
Tier 2: Cobblestone (40%), Iron (25%), Gold (15%), Lapis (10%), Diamond (5%), Linh Thạch Quặng (5%)
Tier 3: Iron (20%), Gold (15%), Diamond (15%), Emerald (10%), Linh Thạch Quặng (20%), Huyền Thiết (10%), Tinh Kim (10%)
Tier 4: Diamond (15%), Emerald (10%), Linh Thạch Quặng (25%), Huyền Thiết (15%), Tinh Kim (15%), Long Tinh (10%), Netherite (10%)
Tier 5: Linh Thạch Quặng (30%), Huyền Thiết (20%), Tinh Kim (15%), Long Tinh (15%), Netherite (10%), Tiên Quặng (10%)
Tier 6: Tất cả custom ore + Thần Quặng (5%)
```

---

## 4. Hệ Thống Dungeon

### 4.1 Cấu Trúc Dungeon

Sử dụng **TurtleDungeon** plugin (có sẵn trên server). Dungeon **KHÔNG giới hạn lượt chơi** — người chơi có thể cày thoải mái, nhưng chỉ vào được dungeon phù hợp **cảnh giới** và **Thực Lực**.

Hệ thống gồm **10 Tầng Dungeon** + **3 Secret Dungeon**:

| Tầng | Tên | Cảnh Giới Yêu Cầu | ⚔ Thực Lực YC | Mob Level | Boss |
|------|-----|--------------------|--------------|-----------|------|
| 1 | **Yêu Thú Lâm** | Luyện Khí Sơ Kỳ | 500 | 50-100 | Sói Vương |
| 2 | **Âm Hồn Động** | Luyện Khí Hậu Kỳ | 1,500 | 80-130 | Quỷ Hồn |
| 3 | **Hỏa Diễm Sơn** | Trúc Cơ Sơ Kỳ | 3,000 | 120-200 | Viêm Ma |
| 4 | **Băng Phong Cốc** | Trúc Cơ Hậu Kỳ | 6,000 | 160-250 | Băng Long |
| 5 | **Huyết Nguyệt Đàn** | Kim Đan Sơ Kỳ | 12,000 | 200-350 | Huyết Tộc Chúa |
| 6 | **Lôi Đình Tháp** | Kim Đan Hậu Kỳ | 25,000 | 260-400 | Lôi Thú |
| 7 | **Vạn Độc Trì** | Nguyên Anh Sơ Kỳ | 50,000 | 320-500 | Độc Tôn |
| 8 | **Long Cốt Mộ** | Nguyên Anh Hậu Kỳ | 100,000 | 380-600 | Cổ Long |
| 9 | **Tiên Phế Đô** | Hóa Thần | 200,000 | 450-750 | Đọa Tiên |
| 10 | **Hỗn Nguyên Giới** | Luyện Hư | 400,000 | 580-1000 | Hỗn Nguyên Thú |

> [!CAUTION]
> **Vào Dungeon yêu cầu CẢ HAI điều kiện:** Cảnh Giới (đủ Tu Vi đã đột phá) **VÀ** Thực Lực tối thiểu.
> Nếu đủ cảnh giới nhưng Thực Lực chưa đủ → **Không vào được** → Phải cường hóa gear, nhập thần, nâng pet...
> Đây là động lực để người chơi **đầu tư vào TẤT CẢ hệ thống**, không chỉ cày Tu Vi.

> [!NOTE]
> **Không giới hạn lượt chơi!** Người chơi cày dungeon thoải mái, nhưng phần thưởng có **Diminishing Returns** — mỗi lần clear thêm trong ngày, drop rate giảm dần:
> - Lần 1-3: 100% drop rate
> - Lần 4-6: 70% drop rate  
> - Lần 7-10: 40% drop rate
> - Lần 11+: 20% drop rate
>
> → Vẫn cày được, nhưng hiệu quả giảm → VIP tăng drop rate bù lại

### 4.2 Secret Dungeon (Bí Cảnh)

| Bí Cảnh | Yêu Cầu Đặc Biệt | Phần Thưởng Đặc Biệt |
|---------|-------------------|----------------------|
| **Cổ Mộ Bí Cảnh** | Tìm 4 mảnh bản đồ (drop từ Tầng 3-6) | Set trang bị Cổ Đại |
| **Long Cung** | Cảnh giới Nguyên Anh + Chìa khóa Long Cung (craft) | Pet Rồng Legendary |
| **Thiên Đạo Thí Luyện** | Cảnh giới Đại Thừa + 1M Linh Thạch phí vào | Tiên Khí vũ khí, danh hiệu |

### 4.3 Dungeon Stages (Giai Đoạn)

Mỗi Dungeon sử dụng hệ thống **Stage** từ TurtleDungeon:

```
Stage 1: KILLMOBS    → Tiêu diệt 30-100 quái (tùy tầng)
Stage 2: KILLTYPE    → Tiêu diệt loại quái cụ thể (Elite mobs)
Stage 3: CAPTUREHEART → Chiếm giữ điểm trong 60-120 giây
Stage 4: KILLBOSS    → Đánh Boss cuối
```

**Chế độ khó (Hard Mode):**
- Mở khóa sau khi clear Normal lần đầu
- Mob +50% HP, +30% DMG
- Boss có thêm phase 2
- Drop rate tăng gấp đôi
- Thêm cơ hội drop vật phẩm Legendary

### 4.4 Phần Thưởng Dungeon

| Loại | Drop Rate | Ví Dụ |
|------|-----------|-------|
| **Linh Thạch** | 100% | 100-10,000 tùy tầng |
| **Tu Vi (EXP)** | 100% | 200-5,000 |
| **Nguyên Liệu Craft** | 60-80% | Huyền Thiết, Tinh Kim, Long Tinh |
| **Đột Phá Đan** | 10-30% | Cần cho đột phá cảnh giới |
| **MMOItems Gear** | 5-20% | Vũ khí, giáp theo tier |
| **Pet Egg** | 1-5% | Trứng Linh Thú |
| **Skill Book** | 3-10% | Sách Công Pháp |
| **Crate Key** | 15-40% | Chìa khóa rương thưởng |
| **Legendary Drop** | 0.5-2% | Vũ khí/giáp đặc biệt, Pet hiếm |

### 4.5 Party System

- Tối đa **4 người** trong 1 party dungeon
- Party thành viên cùng đảo: **+20% drop bonus**
- Difficulty scale theo số người: Solo (100%) → 2P (180%) → 3P (260%) → 4P (340%)
- Phần thưởng chia đều, ai cũng nhận full EXP

---

## 5. Liên Kết Skyblock ↔ Dungeon

> [!IMPORTANT]
> Đây là **trái tim** của gameplay — hai hệ thống phải liên kết chặt chẽ để người chơi luôn có lý do chuyển đổi qua lại.

### 5.1 Skyblock → Dungeon (Đảo cung cấp cho Dungeon)

| Từ Skyblock | Dùng Trong Dungeon |
|-------------|-------------------|
| **Linh Dược** (farm trên đảo) | Craft **Hồi Phục Đan** (heal pot), **Tăng Lực Đan** (buff pot) |
| **Khoáng Sản** (mine trên đảo) | Craft/nâng cấp **vũ khí, giáp** MMOItems |
| **Spawner mob drops** | Craft **Triệu Hồi Phù** (revive token trong dungeon) |
| **Island Level cao** | Mở khóa **buff đảo** áp dụng cả trong dungeon |
| **Linh Thạch** (kiếm từ đảo) | Phí vào Dungeon tầng cao, repair trang bị |

### 5.2 Dungeon → Skyblock (Dungeon cung cấp cho Đảo)

| Từ Dungeon | Dùng Trên Skyblock |
|------------|-------------------|
| **Nguyên liệu hiếm** | Nâng cấp Island Tier, craft advanced tools |
| **Spawner drops** | Đặt spawner hiếm trên đảo (Blaze, Enderman, Wither Skeleton) |
| **Enchant Books** | Enchant công cụ farming, mining hiệu quả hơn |
| **MMOItems trang bị** | Tăng hiệu suất farming (Fortune+, Haste+) |
| **Đột Phá Đan** | Lên cảnh giới → mở đảo Tier cao hơn |
| **Bản thiết kế** (Blueprint) | Mở khóa machine/autocraft trên đảo |

### 5.3 Vòng Lặp Liên Kết

```
    ┌──────────────────────────────────────────────────┐
    │                                                  │
    ▼                                                  │
 SKYBLOCK                                          DUNGEON
 ─────────                                         ────────
 Farm Linh Dược ──► Craft Potion ──────────────────► Dùng trong Dungeon
 Mine Khoáng Sản ──► Craft Vũ Khí ─────────────────► Trang bị vào Dungeon
                                                       │
 Đặt Spawner ◄────── Drop Spawner ◄────────────────── Drop từ Boss
 Nâng Island Tier ◄─ Đột Phá Đan ◄─────────────────── Drop từ Dungeon
 Craft Advanced ◄─── Blueprint ◄────────────────────── Phần thưởng clear
    │                                                  ▲
    │                                                  │
    └──────────────────────────────────────────────────┘
```

### 5.4 Weekly Island Dungeon Challenge

Mỗi tuần, đảo có thể mở **Đảo Dungeon Thử Thách**:
- Tất cả thành viên đảo tham gia
- Boss HP scale theo Island Level
- Phần thưởng cho toàn bộ đảo: Island EXP, Linh Thạch kho đảo, Island upgrade points
- Tạo cạnh tranh giữa các đảo → **Island Dungeon Ranking**

---

## 6. Hệ Thống Vũ Khí & Trang Bị (Linh Khí)

### 6.1 Tier Trang Bị

Sử dụng **MMOItems** plugin:

| Tier | Tên | Màu | Nguồn | Cảnh Giới YC |
|------|-----|-----|-------|-------------|
| ⬜ | **Phàm Phẩm** (Common) | &f | Craft cơ bản | Phàm Nhân |
| 🟢 | **Hạ Phẩm** (Uncommon) | &a | Craft, Dungeon T1-2 | Luyện Khí |
| 🔵 | **Trung Phẩm** (Rare) | &9 | Dungeon T3-4 | Trúc Cơ |
| 🟣 | **Thượng Phẩm** (Epic) | &5 | Dungeon T5-6 | Kim Đan |
| 🟡 | **Cực Phẩm** (Legendary) | &6 | Dungeon T7-8, Craft đặc biệt | Nguyên Anh |
| 🔴 | **Tiên Phẩm** (Mythic) | &c | Dungeon T9-10, Bí Cảnh | Hóa Thần |
| 🌈 | **Thần Phẩm** (Divine) | &b&l | Event, Thiên Đạo Thí Luyện | Đại Thừa |

### 6.2 Loại Trang Bị

| Loại | Ví Dụ | Đặc Biệt |
|------|-------|-----------|
| **Kiếm** (Sword) | Thanh Phong Kiếm, Huyết Nhận | Tốc độ đánh nhanh, combo |
| **Đao** (Axe/Blade) | Khai Thiên Đao | Damage cao, chậm |
| **Thương** (Spear/Trident) | Long Nha Thương | Tầm xa, AoE nhẹ |
| **Cung** (Bow) | Liệt Nhật Cung | Ranged, charge shot |
| **Pháp Bảo** (Staff) | Huyền Thiên Trượng | Skill damage + buff |
| **Giáp** (Armor Set) | Huyền Giáp Set | Set bonus khi mặc đủ |
| **Phụ Kiện** (Accessory) | Linh Ngọc, Hộ Thân Phù | Passive buffs |

### 6.3 Hệ Thống Cường Hóa (Enhancement)

| Hệ Thống | Mô Tả | Nguyên Liệu |
|-----------|--------|-------------|
| **Cường Hóa** (+1 đến +15) | Tăng base stat | Cường Hóa Thạch (mine, dungeon) |
| **Tinh Luyện** (Refine) | Thêm random sub-stat | Tinh Luyện Đan (craft từ Linh Dược) |
| **Khảm Nạm** (Socket) | Gắn gem vào socket | Linh Thạch gem (dungeon drop) |
| **Giác Tỉnh** (Awaken) | Mở khóa skill đặc biệt của vũ khí | Giác Tỉnh Phù (Bí Cảnh drop) |

**Cường Hóa có tỉ lệ thất bại:**

| Level | Tỉ Lệ Thành Công | Thất Bại |
|-------|------------------|----------|
| +1 → +5 | 100% | — |
| +6 → +9 | 80% → 50% | Giảm 1 level |
| +10 → +12 | 40% → 20% | Giảm 2 level |
| +13 → +15 | 15% → 5% | Reset về +10 |

> **Bảo Hộ Phù** (Protection Talisman): Ngăn giảm level khi thất bại. Drop từ Dungeon T7+ hoặc Event.

### 6.4 Hệ Thống Nhập Thần (魂入 — Soul Infusion)

> [!IMPORTANT]
> **Nhập Thần** là hệ thống **hy sinh item/trang bị** để **tăng chỉ số vĩnh viễn** cho nhân vật. Item bị tiêu hủy, chỉ số được giữ mãi. Đây là **item sink** chính của server, giúp cân bằng kinh tế và tạo mục tiêu cày dài hạn.

#### 6.4.1 Cơ Chế Hoạt Động

```
┌───────────────────────────────────────────────────────┐
│               GIAO DIỆN NHẬP THẦN                      │
│                                                       │
│   ┌─────────────────────────────────────────┐   │
│   │  [🟢 ATK] [🔵 DEF] [🟡 HP]  [🟣 SPD]         │   │
│   │  [🟠 CRIT] [⚪ LUCK]                          │   │
│   │                                             │   │
│   │  6 Ô NHẬP THẦN (mở theo cảnh giới)         │   │
│   └─────────────────────────────────────────┘   │
│                                                       │
│   ┌─────────┐   Bỏ item vào ô → Xác nhận         │
│   │  ITEM   │   → Item bị tiêu hủy                │
│   │  ⬇️     │   → Chỉ số cộng vĩnh viễn vào ô   │
│   └────────┘                                     │
└───────────────────────────────────────────────────────┘

Người chơi mở GUI Nhập Thần (qua NPC hoặc `/nhapthan`), đặt item vào ô tương ứng, xác nhận → item bị **tiêu hủy vĩnh viễn** và chỉ số được cộng vào nhân vật.

#### 6.4.2 Các Ô Nhập Thần (6 Ô)

| Ô | Tên | Chỉ Số Tăng | Mở Khóa Tại | Nhận Item Loại |
|---|-----|-------------|------------|----------------|
| 🟢 | **Công Lực** (ATK) | +ATK | Luyện Khí (Lv51) | Vũ khí (Kiếm, Đao, Thương, Cung, Pháp Bảo) |
| 🔵 | **Thủ Ngự** (DEF) | +DEF | Luyện Khí (Lv51) | Giáp (Helmet, Chest, Legs, Boots) |
| 🟡 | **Sinh Lực** (HP) | +Max HP | Trúc Cơ (Lv121) | Giáp, Phụ Kiện, Đồ ăn đặc biệt |
| 🟣 | **Tốc Độ** (SPD) | +Speed, +Attack Speed | Kim Đan (Lv201) | Boots, Phụ Kiện, Skill Book |
| 🟠 | **Bạo Kích** (CRIT) | +Crit Rate, +Crit DMG | Nguyên Anh (Lv321) | Vũ khí, Linh Ngọc, Độc dược |
| ⚪ | **Vận Khí** (LUCK) | +Drop Rate, +Linh Thạch | Hóa Thần (Lv451) | Mọi loại item |

#### 6.4.3 Chỉ Số Nhận Được Theo Tier Item

Mỗi item hy sinh cho chỉ số khác nhau tùy **tier và cường hóa**:

| Tier Item | ATK/DEF | HP | SPD | CRIT Rate | LUCK |
|-----------|---------|-----|-----|-----------|------|
| ⬜ Phàm Phẩm | +1 | +5 | +0.5% | +0.1% | +0.1% |
| 🟢 Hạ Phẩm | +3 | +15 | +1% | +0.3% | +0.2% |
| 🔵 Trung Phẩm | +8 | +40 | +2% | +0.5% | +0.5% |
| 🟣 Thượng Phẩm | +20 | +100 | +3% | +1% | +1% |
| 🟡 Cực Phẩm | +50 | +250 | +5% | +2% | +2% |
| 🔴 Tiên Phẩm | +120 | +600 | +8% | +3.5% | +3.5% |
| 🌈 Thần Phẩm | +300 | +1,500 | +12% | +5% | +5% |

**Bonus cường hóa:** Item đã cường hóa cho thêm stat:
- +1 → +5: +10%/level bonus
- +6 → +10: +15%/level bonus
- +11 → +15: +25%/level bonus

> **Ví dụ:** Cực Phẩm Kiếm +10 nhập thần vào ô ATK:
> Base: +50 ATK
> Cường hóa: +5 level × 10% + 5 level × 15% = +125% bonus
> **Tổng: +50 × 2.25 = +112 ATK vĩnh viễn**

#### 6.4.4 Giới Hạn Nhập Thần (Cap Theo Cảnh Giới)

Mỗi ô có **giới hạn chỉ số tối đa**, tăng theo cảnh giới:

| Cảnh Giới | ATK Cap | DEF Cap | HP Cap | SPD Cap | CRIT Cap | LUCK Cap |
|-----------|---------|---------|--------|---------|----------|----------|
| Luyện Khí | 50 | 50 | 200 | — | — | — |
| Trúc Cơ | 150 | 150 | 600 | — | — | — |
| Kim Đan | 400 | 400 | 1,500 | 20% | — | — |
| Nguyên Anh | 1,000 | 1,000 | 4,000 | 40% | 15% | — |
| Hóa Thần | 2,500 | 2,500 | 10,000 | 60% | 30% | 20% |
| Luyện Hư | 5,000 | 5,000 | 20,000 | 80% | 45% | 35% |
| Đại Thừa | 8,000 | 8,000 | 35,000 | 100% | 60% | 50% |
| Tiên Nhân | **15,000** | **15,000** | **60,000** | **120%** | **80%** | **70%** |

> **Tâm lý:** Mỗi lần lên cảnh giới → cap tăng mạnh → cần farm nhiều item hơn để "fill" cap mới
> **Đột phá cảnh giới = mở thêm "đất trống" để cày**

#### 6.4.5 Nhập Thần Cao Cấp (Advanced Infusion)

| Tính Năng | Yêu Cầu | Hiệu Ứng |
|-----------|---------|----------|
| **Nhập Song Thần** | Luyện Hư+ | Nhập 2 item cùng lúc vào 1 ô → +20% bonus |
| **Nhập Thần Hoàn Mỹ** | Item +15 | Nhập item +15 → triple chỉ số (thay vì 2.25×) |
| **Thiên Đạo Nhập** | Tiên Nhân | Bỏ qua cap, cộng thẳng (nhưng EXP giảm 50%) |
| **Chuyển Hóa Nhập Thần** | 500 LN (shop) | Chuyển chỉ số từ ô này sang ô khác (mất 20%) |

#### 6.4.6 Nguồn Item Để Nhập Thần

| Nguồn | Loại Item | Hiệu Quả |
|-------|----------|----------|
| **Dungeon drop** | Vũ khí/giáp các tier | Nguồn chính, cày vô hạn |
| **Craft** | Vũ khí/giáp Phàm-Hạ Phẩm | Rẻ, feedfiller cho giai đoạn đầu |
| **Gacha/Crate** | Gear các tier | Item thừa từ gacha có chỗ dùng |
| **PVP reward** | Gear season | Gear cuối season nhập thần |
| **Trade/Mua** | Mua gear cũ từ người khác | Tạo nhu cầu trade |
| **Nhập Thần Đan** (shop) | Item đặc biệt | 150 LN, cho +30 ATK/DEF trực tiếp |

> [!TIP]
> **Tại sao Nhập Thần gây nghiện:**
> - **Item không bao giờ thừa**: mọi gear dư đều có giá trị → luôn có lý do cày
> - **Tiến bộ vĩnh viễn**: không bao giờ mất chỉ số đã nhập → cảm giác thoả mãn
> - **Cap tăng theo cảnh giới**: luôn có mục tiêu mới → không bao giờ "xong"
> - **Biến gear trash thành giá trị**: trúng gear yếu từ dungeon? Nhập thần!
> - **VIP cày nhiều dungeon = nhiều gear = nhập thần nhanh hơn** → Hút máu tự nhiên
> - **Nhập Thần Đan từ shop** = skip grind bằng tiền → Monetization

---

## 7. Hệ Thống Pet (Linh Thú)

### 7.1 Tổng Quan

Sử dụng **TurtlePet** plugin:

| Rarity | Tên | Nguồn | Base Buff |
|--------|-----|-------|-----------|
| ⬜ Common | Thỏ Linh, Gà Linh | Drop đảo, craft | +3-5% stat |
| 🟢 Uncommon | Sói Linh, Mèo Linh | Dungeon T1-3 | +5-10% stat |
| 🔵 Rare | Phượng Hoàng Con, Hắc Báo | Dungeon T4-6 | +10-15% stat |
| 🟣 Epic | Hỏa Kỳ Lân, Băng Long Con | Dungeon T7-8, Crate | +15-25% stat |
| 🟡 Legendary | Long Vương, Phượng Hoàng | Bí Cảnh, Event | +25-40% stat |
| 🔴 Mythic | Hỗn Nguyên Thú, Kỳ Lân Thần | Ultra-rare event | +40-60% stat |

### 7.2 Pet Mechanics

| Tính Năng | Mô Tả |
|-----------|--------|
| **Pet Slot** | Tối đa 3 slot (mở theo cảnh giới) |
| **Pet Level** | 1-100, lên level bằng cách mang theo farm/dungeon |
| **Pet Evolution** | Ở level 50 và 100, pet tiến hóa hình dạng & buff mạnh hơn |
| **Pet Skill** | Mỗi pet có 1-3 skill — **cả Passive (tự động) & Active (chủ động cast)** |
| **Pet Fusion** | Hy sinh 3 pet cùng rarity → 1 pet rarity cao hơn (60% thành công) |
| **Pet Food** | Craft từ Linh Dược trên đảo → tăng EXP pet |
| **Auto-Attack** | Pet **tự động tấn công** MythicMobs lân cận khi chủ chiến đấu |
| **Skill Cast** | Người chơi **chủ động xả skill pet** bằng hotkey `F` hoặc Pet GUI |

### 7.3 Pet Chiến Đấu (Combat AI)

> [!IMPORTANT]
> **Pet tự động đi đánh MythicMobs gần đó!** Khi chủ chiến đấu hoặc có mob trong phạm vi 15 block, pet tự lao vào tấn công.

**Hành vi AI:**
- Chủ vào vùng có MythicMobs (15 block) → Pet tự target mob gần nhất
- Auto-attack liên tục (DMG theo pet stat)
- Xả **Passive Skill** tự động khi hết CD
- Chủ nhấn **hotkey F** → Pet xả **Active Skill** (mạnh hơn, CD dài)
- Pet chết → Hồi sinh sau 60s (VIP: 30s, hoặc Pet Food hồi ngay)
- Pet HP thấp → Tự lùi về gần chủ, hồi HP dần

| Thông Số | Giá Trị |
|----------|---------|
| Phạm vi detect mob | 15 blocks |
| Phạm vi tấn công | 2-5 blocks (tùy loại) |
| Pet DMG | Base + (Pet Level x hệ số rarity) |
| Pet HP | 50 - 5,000 (theo rarity + level) |
| Hồi sinh | 60s (VIP: 30s) |
| Chế độ | Passive / Aggressive / Defensive (GUI) |

### 7.4 Pet Skills (Kỹ Năng Linh Thú)

**Passive Skills (Tự Động):**

| Skill | Pet | Hiệu Ứng | CD |
|-------|-----|-----------|-----|
| Nanh Độc | Sói Linh | Gây Poison 3s | 10s |
| Khiên Băng | Băng Long Con | Chặn 1 đòn cho chủ | 30s |
| Hỏa Tức | Hỏa Kỳ Lân | AoE lửa 3 block, 50-200 DMG | 15s |
| Hồi Phục | Phượng Hoàng Con | Heal chủ 10% HP | 20s |
| Combo Strike | Hắc Báo | 3 đòn liên tiếp, +20% DMG/đòn | 12s |
| Lôi Kích | Long Vương | AoE sét 5 block, stun 1.5s | 25s |

**Active Skills (Nhấn F để xả):**

| Skill | Pet | Hiệu Ứng | CD | Unlock |
|-------|-----|-----------|-----|--------|
| Gầm Chiến | Sói Linh | Buff ATK +20% cho chủ 10s | 60s | Lv30 |
| Long Tức | Băng Long Con | Phun băng cone, freeze 3s | 45s | Lv50 |
| Phượng Vũ | Phượng Hoàng | AoE lửa rộng, 500-2K DMG | 60s | Lv50 |
| Thiên Lôi | Long Vương | Sét diện rộng, 1K-5K DMG | 90s | Lv70 |
| Hỗn Nguyên Phá | Hỗn Nguyên Thú | Ultimate toàn mob, 3K-10K | 180s | Lv100 |
| Thần Hộ | Kỳ Lân Thần | Bất tử 5s + heal full HP | 300s | Lv100 |

> Mang 3 pet = 3 Active Skill khác nhau → combo build đa dạng

**Scale theo Rarity:**

| Rarity | Passive | Active | DMG x |
|--------|---------|--------|-------|
| Common | 1 | 0 | x0.5 |
| Uncommon | 1 | 1 (Lv30) | x0.8 |
| Rare | 2 | 1 (Lv30) | x1.0 |
| Epic | 2 | 1 (Lv50) | x1.5 |
| Legendary | 3 | 2 (Lv50+Lv70) | x2.0 |
| Mythic | 3 | 2 (Lv50+Lv100) | x3.0 |

### 7.5 Pet Buffs Theo Loại

| Loại Pet | Buff Chính | Tốt Cho |
|----------|-----------|---------|
| **Chiến Đấu** (Combat) | +ATK, +Crit | Dungeon, PVP |
| **Phòng Thủ** (Tank) | +HP, +DEF | Dungeon tank |
| **Thu Hoạch** (Gather) | +Mining Speed, +Farm Speed | Skyblock farming |
| **May Mắn** (Lucky) | +Drop Rate, +Linh Thạch | Cả hai |
| **Hỗ Trợ** (Support) | +Heal, +Party Buff | Party dungeon |

---

## 8. Hệ Thống Skills (Công Pháp)

### 8.1 Skill Tree

Sử dụng **TurtleSkills** plugin, chia thành **4 nhánh Công Pháp**:

#### 🔥 Hỏa Đạo (Fire Path) — Damage dealer
```
Tầng 1: Hỏa Cầu Thuật (Fireball) ─── 50 DMG, 5s CD
   └─ Tầng 2: Liệt Diễm Chưởng (Flame Palm) ─── 120 DMG, AoE, 8s CD
       └─ Tầng 3: Tam Muội Chân Hỏa (True Fire) ─── 300 DMG, DoT, 15s CD
           └─ Tầng 4: Phượng Hoàng Niết Bàn (Phoenix Rebirth) ─── 800 DMG, AoE + self rez, 60s CD
```

#### ❄️ Băng Đạo (Ice Path) — CC/Control
```
Tầng 1: Băng Tiễn (Ice Arrow) ─── 30 DMG + Slow, 4s CD
   └─ Tầng 2: Băng Phong Bão (Blizzard) ─── 80 DMG + AoE Freeze, 10s CD
       └─ Tầng 3: Tuyệt Đối Linh Vực (Absolute Zero) ─── 200 DMG + Stun 3s, 20s CD
           └─ Tầng 4: Băng Phong Thiên Giáng (Ice Apocalypse) ─── 600 DMG + AoE Freeze 5s, 45s CD
```

#### 🛡️ Thổ Đạo (Earth Path) — Tank/Defense
```
Tầng 1: Thạch Bì (Stone Skin) ─── +30% DEF, 10s duration, 15s CD
   └─ Tầng 2: Địa Chấn (Earthquake) ─── 60 DMG + Knockback, 8s CD
       └─ Tầng 3: Bất Động Minh Vương (Immovable King) ─── +80% DEF + Taunt, 12s CD
           └─ Tầng 4: Sơn Hà Xã Tắc (Mountain & River) ─── Team Shield 5s + Reflect, 50s CD
```

#### 💚 Mộc Đạo (Wood Path) — Healer/Support
```
Tầng 1: Linh Dược Thuật (Herb Heal) ─── Heal 50 HP, 6s CD
   └─ Tầng 2: Sinh Cơ (Regeneration) ─── HoT 20HP/s 10s, 12s CD
       └─ Tầng 3: Vạn Mộc Hồi Xuân (Forest Revival) ─── AoE Heal + Cleanse, 18s CD
           └─ Tầng 4: Luân Hồi Mộc (Cycle of Life) ─── Full team rez to 50% HP, 120s CD
```

### 8.2 Skill Slot & Equip

- Mỗi người chơi có **tối đa 4 skill slot** (mở dần theo cảnh giới)
- Có thể mix skill từ các nhánh khác nhau
- Skill lên cấp bằng cách sử dụng + **Skill Point** (nhận khi lên level)
- **Skill Book** drop từ Dungeon để mở khóa skill mới

### 8.3 Passive Skills (Tu Luyện Thụ Động)

Ngoài active skill, người chơi có passive vĩnh viễn:

| Passive | Max Level | Hiệu Ứng | Cách Lên |
|---------|-----------|-----------|----------|
| **Kiếm Thuật** | 50 | +1% Sword DMG/lv | Dùng kiếm |
| **Khoáng Thuật** | 50 | +1% Mining Speed/lv | Đào mỏ |
| **Dược Thuật** | 50 | +1% Potion Effect/lv | Craft potion |
| **Luyện Khí Thuật** | 50 | +1% AFK Tu Vi/lv | Online time |
| **Thú Thuật** | 50 | +1% Pet EXP/lv | Mang pet |

---

## 9. Hệ Thống Kinh Tế

### 9.1 Tiền Tệ

| Loại | Ký Hiệu | Nguồn Chính | Sử Dụng |
|------|---------|-------------|---------|
| **Linh Thạch** 💎 | `LS` | Mining, Dungeon, Trade | Upgrade đảo, craft, mua NPC, phí dungeon |
| **Xu** 🪙 | `Xu` | Quest, PVP, Vote | Mua cosmetic, crate key, gacha |
| **Điểm Danh Vọng** ⭐ | `DV` | Milestone, Achievement | Mua danh hiệu, skin đặc biệt |

### 9.2 Luồng Kinh Tế

```
                    NGUỒN THU
                    ──────────
        Mining ─────────────────────┐
        Dungeon clear ──────────────┤
        Mob farming ────────────────┤──► Linh Thạch
        Trade bán hàng ────────────┤
        Quest ──────────────────────┘

        Quest hàng ngày ────────────┐
        PVP thắng ──────────────────┤──► Xu
        Vote ───────────────────────┤
        Achievement ────────────────┘

                    CHI TIÊU
                    ────────
        Upgrade Island ◄───────────┐
        Craft vũ khí ◄─────────────┤
        Mua NPC shop ◄─────────────┤──► Linh Thạch (SINK chính)
        Cường hóa ◄────────────────┤
        Phí Dungeon cao cấp ◄──────┘

        Crate key ◄────────────────┐
        Cosmetic ◄──────────────────┤──► Xu (SINK phụ)
        Pet gacha ◄─────────────────┘
```

### 9.3 Chợ Giao Dịch (Trade)

- **NPC Shop**: Mua/bán giá cố định (giá thấp hơn thị trường)
- **Chợ Người Chơi**: Đặt bán item với giá tùy chọn (thuế 5%)
- **Đấu Giá**: Item hiếm bán đấu giá, thời gian 24-48h
- Cần cảnh giới **Trúc Cơ** để mở khóa Trade

---

## 10. Hệ Thống Quest

### 10.1 Quest Hàng Ngày (Daily Quest)

Mỗi ngày reset 5 quest ngẫu nhiên từ pool:

| Quest | Yêu Cầu | Phần Thưởng |
|-------|---------|-------------|
| Tu Luyện Cần Cù | AFK 30 phút | 100 Tu Vi + 200 LS |
| Khai Khoáng | Đào 200 ore | 150 Tu Vi + 300 LS |
| Diệt Yêu | Giết 50 mob trên đảo | 100 Tu Vi + 150 LS |
| Luyện Đan | Craft 10 potion | 80 Tu Vi + 250 LS |
| Nông Phu | Thu hoạch 100 Linh Dược | 120 Tu Vi + 200 LS |
| Dungeon Tập Sự | Clear 1 dungeon bất kỳ | 300 Tu Vi + 500 LS |
| Giao Thương | Bán 5 item trên chợ | 50 Tu Vi + 400 LS |
| Luyện Pet | Pet lên 1 level | 100 Tu Vi + 100 LS |

**Daily Streak Bonus:**
- 3 ngày liên tiếp: +50% phần thưởng quest
- 7 ngày liên tiếp: Random Crate Key
- 14 ngày liên tiếp: 1 Pet Egg (Uncommon+)
- 30 ngày liên tiếp: 1 Legendary Crate Key + 50,000 LS

### 10.2 Quest Tuần (Weekly Quest)

3 quest mỗi tuần, khó hơn, thưởng lớn hơn:

| Quest | Yêu Cầu | Phần Thưởng |
|-------|---------|-------------|
| Chinh Phục | Clear 10 Dungeon | 3,000 Tu Vi + 5,000 LS + Crate Key |
| Đại Thương Gia | Kiếm 50,000 LS từ Trade | 2,000 Tu Vi + Xu 500 |
| Đảo Chủ | Nâng Island Level +5 | 2,500 Tu Vi + 3,000 LS + Blueprint Random |

### 10.3 Quest Chính Tuyến (Main Story)

Chuỗi quest dẫn dắt người chơi qua toàn bộ nội dung:

```
📖 Chương 1: Khởi Đầu Tu Tiên
   ├─ Q1: Xây dựng đảo cơ bản (Island Tier 1)
   ├─ Q2: Farm 100 Linh Dược đầu tiên
   ├─ Q3: Craft vũ khí Phàm Phẩm đầu tiên
   └─ Q4: Đột phá Luyện Khí ★ Thưởng: Pet Egg + 5,000 LS

📖 Chương 2: Bước Vào Tu Đạo
   ├─ Q5: Clear Dungeon Tầng 1 (Yêu Thú Lâm)
   ├─ Q6: Tìm 1 Skill Book và trang bị skill
   ├─ Q7: Nâng Pet lên level 10
   └─ Q8: Đột phá Trúc Cơ ★ Thưởng: Hạ Phẩm Weapon + 10,000 LS

📖 Chương 3: Kim Đan Đại Đạo
   ├─ Q9: Nâng Island lên Tier 3
   ├─ Q10: Clear Dungeon Tầng 5 (Hard Mode)
   ├─ Q11: Cường hóa vũ khí lên +7
   └─ Q12: Đột phá Kim Đan ★ Thưởng: Epic Weapon + Pet Epic + 50,000 LS

📖 Chương 4: Nguyên Anh Hiển Thế
   ├─ Q13: Party clear Dungeon Tầng 7
   ├─ Q14: Hoàn thành 1 Bí Cảnh (Secret Dungeon)
   ├─ Q15: Thắng 10 trận PVP Arena
   └─ Q16: Đột phá Nguyên Anh ★ Thưởng: Legendary Weapon + 200,000 LS

📖 Chương 5: Tiên Đạo Vô Tận
   ├─ Q17: Clear Dungeon Tầng 10
   ├─ Q18: Nâng Island lên Tier 6
   ├─ Q19: Sở hữu 1 Legendary Pet level 100
   └─ Q20: Đột phá Tiên Nhân ★ Thưởng: Divine Weapon + Danh hiệu "Tiên Nhân" + 1,000,000 LS
```

---

## 11. Hệ Thống Rank & Danh Hiệu

### 11.1 Rank Ingame (LuckPerms)

| Rank | Điều Kiện | Prefix | Đặc Quyền |
|------|-----------|--------|-----------|
| Phàm Nhân | Mặc định | `&7[Phàm]` | Cơ bản |
| Tu Sĩ | Luyện Khí+ | `&a[Tu Sĩ]` | 2 homes, /warp |
| Đạo Sĩ | Trúc Cơ+ | `&9[Đạo Sĩ]` | 3 homes, /trade |
| Chân Nhân | Kim Đan+ | `&5[Chân Nhân]` | 5 homes, /fly on island |
| Đại Năng | Nguyên Anh+ | `&6[Đại Năng]` | 7 homes, /nick |
| Tiên Tôn | Hóa Thần+ | `&c[Tiên Tôn]` | 10 homes, custom prefix color |
| Thần | Đại Thừa+ | `&b[Thần]` | 15 homes, all commands |
| Tiên Đế | Tiên Nhân Viên Mãn | `&b&l✦[Tiên Đế]✦` | Unlimited, special effects |

### 11.2 Danh Hiệu (Titles)

Danh hiệu hiển thị dưới tên, thu thập qua thành tích:

| Danh Hiệu | Điều Kiện | Buff |
|-----------|-----------|------|
| 🐉 Long Sát | Clear Dungeon 8 (Hard Mode) | +5% DMG to Boss |
| ⚔️ Kiếm Thánh | Cường hóa vũ khí +15 | +10% Sword DMG |
| 🏝️ Vạn Đảo Chi Vương | Island Level 100 | +10% Island Generation |
| 🐾 Ngự Thú Sư | 5 Pet Legendary+ | +15% Pet EXP |
| 💀 Sát Thần | 1000 PVP Kills | +5% PVP DMG |
| 🌟 Tiên Nhân | Đột phá Tiên Nhân | +20% tất cả stat |
| 🏆 Thiên Hạ Đệ Nhất | Top 1 Lực Chiến | Hiệu ứng particle đặc biệt |

---

## 12. Hệ Thống PVP — Lôi Đài

### 12.1 PVP Arena

- Vị trí: `/warp pvp`
- Yêu cầu: Cảnh giới **Nguyên Anh+**
- Khu vực tự do PVP, không drop item
- Giết người → nhận Xu + Tu Vi

### 12.2 Lôi Đài (Ranked PVP)

| Season | Thời Gian | Phần Thưởng Top |
|--------|-----------|-----------------|
| Mùa | 30 ngày | Top 1: Divine Weapon + 500K LS |
| | | Top 2-3: Legendary Weapon + 200K LS |
| | | Top 4-10: Epic Weapon + 100K LS |
| | | Top 11-50: 50K LS + Crate Key |

**Hệ thống ELO:**
- Bắt đầu: 1000 ELO
- Thắng: +15-30 ELO (tùy đối thủ)
- Thua: -10-20 ELO
- Rank: Bronze → Silver → Gold → Platinum → Diamond → Immortal

### 12.3 Cân Bằng PVP

- Trong Lôi Đài, trang bị được normalize theo tier (không phải +15 vs +1)
- Skill vẫn giữ nguyên → khuyến khích đa dạng build
- Pet buff giảm 50% trong PVP
- Potion effect bị giới hạn

---

## 13. Hệ Thống Ruộng Linh Dược (Farm)

### 13.1 Loại Linh Dược

| Linh Dược | Thời Gian Trồng | Giá Bán (LS) | Công Dụng |
|-----------|-----------------|-------------|-----------|
| **Linh Thảo** | 10 phút | 5 | Craft Heal Pot cơ bản |
| **Huyết Liên** | 30 phút | 15 | Craft Heal Pot trung |
| **Hỏa Linh Chi** | 1 giờ | 40 | Craft Tăng Lực Đan |
| **Băng Tâm Thảo** | 2 giờ | 80 | Craft Phòng Thủ Đan |
| **Cửu Diệp Liên** | 4 giờ | 200 | Craft Đột Phá Đan |
| **Thiên Niên Nhân Sâm** | 12 giờ | 500 | Craft Ultimate Potion |
| **Vạn Năm Linh Chi** | 24 giờ | 1,500 | Craft Tiên Đan |
| **Hỗn Nguyên Hoa** | 48 giờ | 5,000 | Craft Divine Potion |

### 13.2 Farm Mechanics

- Mỗi farm plot: 3x3 blocks (9 ô trồng)
- Có thể tưới nước (Linh Tuyền) → giảm 20% thời gian
- Bón phân (Linh Thổ) → +30% sản lượng
- Pet Thu Hoạch → auto harvest khi offline
- Linh Dược cấp cao cần cảnh giới tương ứng mới trồng được

---

## 14. Hệ Thống Sự Kiện

### 14.1 Sự Kiện Cố Định (Scheduled)

| Sự Kiện | Tần Suất | Mô Tả |
|---------|---------|-------|
| **Thiên Giáng Linh Vũ** | Mỗi 6 giờ | 10 phút mining buff toàn server (+50% drop) |
| **Yêu Thú Xâm Lược** | Mỗi 4 giờ | Mob đặc biệt spawn, giết → rare drops |
| **Linh Thạch Đại Hội** | Mỗi 8 giờ | Mini-game mining competition → Top 3 thưởng lớn |
| **Boss Thế Giới** (World Boss) | Mỗi 12 giờ | Boss khổng lồ, cả server tham gia, top DMG nhận thưởng |

### 14.2 Sự Kiện Mùa (Seasonal)

| Sự Kiện | Thời Gian | Nội Dung |
|---------|-----------|----------|
| **Hội Đấu Tông Môn** | 2 tuần/lần | Tournament PVP giữa các đảo, phần thưởng đảo |
| **Bí Cảnh Hạn Chế** | 1 tháng/lần | Dungeon đặc biệt chỉ mở trong 3 ngày |
| **Thiên Kiếp Giáng Lâm** | Tết/Lễ | Event Boss cực mạnh, drop vật phẩm limited |
| **Linh Thú Đại Hội** | 2 tuần/lần | Pet PvE competition, xếp hạng pet mạnh nhất |

### 14.3 Daily Login Rewards

| Ngày | Phần Thưởng |
|------|-------------|
| 1 | 500 LS |
| 2 | 1,000 LS |
| 3 | Common Crate Key |
| 4 | 2,000 LS |
| 5 | Uncommon Crate Key |
| 6 | 3,000 LS |
| 7 | **Rare Crate Key + 5,000 LS + Pet Food x10** |
| 14 | **Epic Crate Key + 20,000 LS** |
| 30 | **Legendary Crate Key + 100,000 LS + Random Skill Book** |

---

## 15. Vòng Lặp Gameplay & Retention

### 15.1 Core Loop (Vòng lặp cốt lõi)

```
          ┌───── FARM (Skyblock) ─────┐
          │   Mine, trồng, craft      │
          │   → Thu thập tài nguyên   │
          ▼                           │
    ┌── TRANG BỊ ──┐           ┌── BÁN/TRADE ──┐
    │  Craft gear   │           │  Bán Linh Dược │
    │  Upgrade      │           │  Đổi tài nguyên│
    └──────┬───────┘           └───────┬────────┘
           │                           │
           ▼                           │
    ┌── DUNGEON ───┐                   │
    │  PvE combat  │                   │
    │  Boss kill   │                   │
    └──────┬───────┘                   │
           │                           │
           ▼                           │
    ┌── PHẦN THƯỞNG ──┐               │
    │  Gear drops      │               │
    │  Đột Phá Đan    │───────────────┘
    │  Pet, Skill      │
    └──────┬───────────┘
           │
           ▼
    ┌── ĐỘT PHÁ ──┐
    │  Lên cảnh giới│
    │  Mở nội dung  │──── LOOP LẠI với nội dung mới ───►
    │  mới          │
    └───────────────┘
```

### 15.2 Engagement Hooks (Móc Giữ Chân)

| Hook | Cơ Chế | Tâm Lý |
|------|--------|--------|
| **Daily Login** | Streak reward tăng dần, reset nếu miss | Loss aversion |
| **Dungeon Diminishing** | Drop rate giảm dần sau lần 3 mỗi ngày | "Cày thêm 1 lần nữa dù drop thấp" |
| **Cường Hóa RNG** | Tỉ lệ thành công giảm dần | Gambling dopamine |
| **Pet Gacha** | Fusion 60% thành công | "Thêm 1 lần nữa" |
| **World Boss Timer** | Mỗi 12h, miss = mất thưởng | FOMO |
| **Season Ranking** | Reset hàng tháng | Cạnh tranh liên tục |
| **Story Quest** | "Chương tiếp theo" cliff-hanger | Curiosity |
| **Island Ranking** | Bảng xếp hạng đảo | Social competition |
| **Linh Dược Timer** | Cây cần thời gian thực | Quay lại check |
| **Streak Bonus** | Quest streak nhân thưởng | Consistency |
| **Limited Event** | Chỉ mở 3 ngày/tháng | Urgency |

### 15.3 Progression Pacing (Nhịp Tiến Bộ)

| Giai Đoạn | Thời Gian | Nội Dung Chính |
|-----------|-----------|----------------|
| **Newbie** (Ngày 1-3) | 3 ngày | Tutorial, đảo cơ bản, Phàm Nhân → Luyện Khí |
| **Early** (Ngày 4-14) | ~10 ngày | Dungeon T1-4, Island Tier 2, First Pet, First Skill |
| **Mid** (Ngày 15-45) | ~30 ngày | Dungeon T5-7, Kim Đan+, Pet Epic, Gear +7-10 |
| **Late** (Ngày 46-90) | ~45 ngày | Dungeon T8-10, Bí Cảnh, PVP, Nguyên Anh+ |
| **Endgame** (90+ ngày) | Ongoing | Min-max gear +15, Tiên Nhân, Rankings, Events |

### 15.4 Retention Milestones

```
Ngày 1:  ★ Đầu tiên — Đảo đầu tiên, cảm giác bắt đầu mới
Ngày 3:  ★ Đột phá đầu tiên — Luyện Khí, mở Dungeon
Ngày 7:  ★ First Dungeon Clear — Loot dopamine
Ngày 14: ★ First Pet — Companion attachment
Ngày 30: ★ Kim Đan — "Nửa đường rồi, không thể bỏ"
Ngày 60: ★ Bí Cảnh + PVP — Social hooks activate
Ngày 90: ★ Endgame chase — Perfection, rankings
```

---

## 16. Plugin Mapping

### Bảng Plugin → Hệ Thống

| Hệ Thống | Plugin Chính | Plugin Hỗ Trợ |
|-----------|-------------|---------------|
| **Tu Luyện / Level** | TurtleLevel | PlaceholderAPI, LuckPerms |
| **Skyblock** | SuperiorSkyblock2 | WorldGuard, VoidGen, FancyHolograms |
| **Dungeon** | TurtleDungeon | MythicMobs, MythicLib |
| **Vũ Khí / Trang Bị** | MMOItems | MythicLib, NBTAPI |
| **Pet** | TurtlePet | TurtlePetGenerator |
| **Skills** | TurtleSkills | MythicLib |
| **Kinh Tế** | PlayerPoints, Essentials | TurtleStorage |
| **Crate / Gacha** | ExcellentCrates | — |
| **Quest** | *Cần thêm plugin* | PlaceholderAPI |
| **Menu / GUI** | DeluxeMenus | HeadDatabase |
| **Rank** | LuckPerms | TAB, PlaceholderAPI |
| **NPC** | FancyNpcs | FancyHolograms |
| **PVP Arena** | *Cần thêm plugin* | WorldGuard |
| **Portal** | TurtleCustomPortals | Multiverse-Core |
| **Damage Display** | TurtleDamageIndicator | — |
| **Build Tool** | FastAsyncWorldEdit | AxiomPaper |

> [!WARNING]
> ### Plugin Cần Bổ Sung
> - **Quest Plugin**: Cần BetonQuest hoặc QualityQuests cho hệ thống quest phức tạp
> - **PVP Arena Plugin**: Cần BattleArena hoặc tương đương cho ranked PVP
> - **AFK Reward**: TurtleAFK (đã có trên server SMP?) hoặc tương đương
> - **Auction House**: Cần plugin đấu giá (Auction House plugin)
> - **Custom Enchants**: Có thể cần ExcellentEnchants cho enchant đặc biệt

---

## 📎 Phụ Lục

### A. Công Thức Thực Lực (Chi Tiết)

```
THỰC LỰC = Gear_Score + Nhập_Thần_Score + Pet_Score + Cảnh_Giới_Bonus + (Tu_Vi ÷ 200)

--- GEAR SCORE ---
Gear_Score = Tổng(Tier_Base x Enhancement_Multiplier) cho mỗi trang bị đang mặc
  Tier Base: Phàm=50, Hạ=150, Trung=400, Thượng=1000, Cực=2500, Tiên=5000, Thần=10000
  Enhancement: +1=x1.1, +5=x1.5, +10=x2.5, +15=x4.0

--- NHẬP THẦN SCORE ---
Nhập_Thần_Score = Tổng chỉ số 6 ô nhập thần (ATK + DEF + HP + SPD + CRIT + LUCK)

--- PET SCORE ---
Pet_Score = Tổng(Rarity_Base x Pet_Level / 10) cho mỗi pet đang mang
  Rarity: Common=100, Uncommon=300, Rare=800, Epic=2000, Legendary=5000, Mythic=12000

--- CẢNH GIỚI BONUS ---
Phàm Nhân=0, Luyện Khí=200, Trúc Cơ=600, Kim Đan=1500, Nguyên Anh=3000,
Hóa Thần=6000, Luyện Hư=12000, Đại Thừa=25000, Độ Kiếp=40000,
Tiên Nhân=60000, Thiên Tiên=90000, Kim Tiên=130000, Thái Ất=180000,
Đại La=250000, Chuẩn Thánh=350000, Thánh Nhân=500000,
Thiên Đế=700000, Đạo Tổ=1000000, Hồng Mông=1500000
```
### B. Bảng Tỉ Lệ Drop Dungeon Chi Tiết

| Item | T1-2 | T3-4 | T5-6 | T7-8 | T9-10 |
|------|------|------|------|------|-------|
| Linh Thạch | 100-300 | 300-800 | 800-2K | 2K-5K | 5K-10K |
| Đột Phá Đan | 10% | 15% | 20% | 25% | 30% |
| Gear (tier tương ứng) | 15% | 12% | 10% | 8% | 5% |
| Pet Egg | 3% | 3% | 4% | 5% | 5% |
| Skill Book | 5% | 5% | 7% | 8% | 10% |
| Crate Key | 30% | 25% | 20% | 15% | 15% |
| Mảnh Bản Đồ Bí Cảnh | — | — | 2% | 3% | 5% |
| Legendary Item | — | — | — | 0.5% | 2% |

### C. NPC Shop Locations

| NPC | Vị Trí | Chức Năng |
|-----|--------|-----------|
| **Lão Trúc** | Spawn | Hướng dẫn newbie, bán basic items |
| **Tiên Nương** | Spawn | Quest NPC |
| **Vương Thiết** | /warp trade | Mua/bán trang bị |
| **Dược Sư** | /warp trade | Mua/bán Linh Dược, Potion |
| **Thú Sư** | /warp trade | Pet shop, Pet food |
| **Lôi Sư** | /warp pvp | PVP arena NPC |
| **Dungeon Thủ** | /warp dungeon | Vào dungeon, mua phí vào |

---

## 17. 💰 Hệ Thống Nạp Tiền & VIP (Monetization)

> [!CAUTION]
> **Triết lý hút máu:** Người chơi FREE vẫn chơi được, nhưng phải cày **gấp 5-10 lần** so với VIP. Mọi pain point trong gameplay đều có giải pháp trả phí. Không bán trực tiếp sức mạnh — bán **tốc độ, tiện ích, và ngoại hình**.

### 17.1 Tiền Tệ Nạp — Linh Ngọc (💎 Premium Currency)

| Gói Nạp | Giá (VNĐ) | Linh Ngọc | Bonus Lần Đầu | Bonus Thường |
|---------|-----------|-----------|---------------|-------------|
| Thử Nghiệm | 20,000 | 200 | +200 (×2) | +20 (10%) |
| Tiểu Gia | 50,000 | 550 | +550 (×2) | +55 (10%) |
| Thiếu Gia | 100,000 | 1,200 | +1,200 (×2) | +180 (15%) |
| Đại Gia | 200,000 | 2,600 | +2,600 (×2) | +520 (20%) |
| Hào Môn | 500,000 | 7,000 | +7,000 (×2) | +2,100 (30%) |
| Thần Hào | 1,000,000 | 15,000 | +15,000 (×2) | +6,000 (40%) |

> **Bonus lần đầu ×2** → Tâm lý "lần đầu quá hời, phải nạp thử" → Hook nạp lần đầu
> **Bonus tăng theo gói** → Càng nạp nhiều càng "hời" → Upsell lên gói cao

**Đặc biệt — Thẻ Tháng (Monthly Card):**

| Loại | Giá | Nhận Ngay | Nhận Mỗi Ngày (30 ngày) | Tổng |
|------|-----|-----------|------------------------|------|
| **Thẻ Tháng Bạc** | 50,000 VNĐ | 300 LN | 50 LN/ngày | 1,800 LN |
| **Thẻ Tháng Vàng** | 100,000 VNĐ | 800 LN | 120 LN/ngày + 1 Crate Key | 4,400 LN + 30 Key |

> **Phải login mỗi ngày để nhận** → Retention hook cực mạnh
> **Giá trị gấp 3-4× so với nạp thẳng** → Ai cũng thấy "hời" → Conversion rate cao nhất

---

### 17.2 Gói VIP (Vĩnh Viễn)

| VIP | Giá (LN) | Giá (VNĐ tương đương) | Đặc Quyền |
|-----|---------|----------------------|-----------|
| 🥉 **VIP 1 — Ngoại Môn** | 500 | ~50K | Cơ bản |
| 🥈 **VIP 2 — Nội Môn** | 1,500 | ~130K | Khá |
| 🥇 **VIP 3 — Chân Truyền** | 3,500 | ~290K | Mạnh |
| 💎 **VIP 4 — Trưởng Lão** | 7,000 | ~530K | Rất mạnh |
| 👑 **VIP 5 — Tông Chủ** | 15,000 | ~1M | Siêu VIP |

#### Chi Tiết Đặc Quyền VIP

| Đặc Quyền | Free | VIP1 | VIP2 | VIP3 | VIP4 | VIP5 |
|-----------|------|------|------|------|------|------|
| **Dungeon Drop Bảo Toàn** | 3 lần | 5 lần | 7 lần | 10 lần | 15 lần | **∞ (không giảm)** |
| **Tu Vi Bonus** | 0% | +10% | +20% | +35% | +50% | +80% |
| **Drop Rate Bonus** | 0% | +5% | +10% | +15% | +25% | +40% |
| **Linh Thạch Bonus** | 0% | +10% | +15% | +25% | +35% | +50% |
| **AFK Tu Luyện Giới Hạn** | 8h | 10h | 12h | 16h | 20h | 24h |
| **Pet Slot Bonus** | 0 | 0 | +1 | +1 | +2 | +2 |
| **Skill Slot Bonus** | 0 | 0 | 0 | +1 | +1 | +2 |
| **Farm Plot Bonus** | 0 | +1 | +2 | +4 | +6 | +10 |
| **Island Size Bonus** | 0 | +10% | +20% | +30% | +40% | +50% |
| **/fly trên đảo** | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **/heal (CD 10m)** | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Auto-collect Linh Dược** | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Dungeon Auto-Loot** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Prefix đặc biệt** | ❌ | 🥉 | 🥈 | 🥇 | 💎 | 👑 |
| **Particle effect** | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ (chọn) |
| **Kho riêng (Ender Chest)** | 1 row | 2 row | 3 row | 4 row | 5 row | 6 row |
| **Daily Bonus Linh Ngọc** | 0 | 5 | 10 | 20 | 35 | 50 |
| **Cường Hóa bảo vệ** | ❌ | ❌ | ❌ | -5% penalty | -10% penalty | -15% penalty |
| **Trade thuế giảm** | 5% | 4% | 3% | 2% | 1% | 0% |

> **Thiết kế:** VIP1-2 rẻ, dễ mua → hook lần đầu. VIP3+ tạo khoảng cách lớn → FOMO upgrade.
> **VIP5 Dungeon Drop không giảm** = ultimate whale bait, cày vô hạn full reward.
> **Dungeon Drop Bảo Toàn** = số lần clear/ngày giữ drop rate 100% (Free chỉ 3 lần, sau đó giảm dần).

---

### 17.3 Battle Pass — Tu Tiên Lệnh (Mỗi Mùa 30 Ngày)

| Loại | Giá | Nội Dung |
|------|-----|----------|
| **Free Pass** | 0 | 30 tầng thưởng cơ bản (Linh Thạch, EXP, materials) |
| **Tu Tiên Lệnh** | 1,000 LN (~100K VNĐ) | 30 tầng thưởng Premium + tất cả Free |
| **Tu Tiên Lệnh Cao Cấp** | 2,500 LN (~200K VNĐ) | Premium + Skip 10 tầng + Skin độc quyền |

#### Phần Thưởng Battle Pass Theo Tầng

| Tầng | Free Track | Premium Track |
|------|-----------|---------------|
| 1-5 | 500 LS mỗi tầng | 200 LN + Uncommon Crate Key |
| 10 | 2,000 LS | **Pet Egg Epic** + 500 LN |
| 15 | 5,000 LS | **Skill Book Rare** + Bảo Hộ Phù x3 |
| 20 | Common Key | **Legendary Crate Key** + 1,000 LN |
| 25 | 10,000 LS | **Trang bị Epic Exclusive** (chỉ có qua BP) |
| 30 | 20,000 LS | **👑 Skin Vũ Khí Exclusive + Pet Legendary + 2,000 LN** |

> **Tâm lý:** Người chơi cày free track → thấy premium track hấp dẫn hơn gấp 10 lần
> **Exclusive items** chỉ có qua BP → Miss = mất vĩnh viễn → FOMO cực mạnh
> **Mua muộn vẫn nhận hết** → Cho phép mua BP ngày cuối, nhận tất cả tầng đã qua

---

### 17.4 Gacha — Thiên Cơ Luân (Vòng Quay May Mắn)

#### 🎰 Thiên Cơ Luân Thường (Linh Ngọc Gacha)

| Lượt Quay | Giá |
|-----------|-----|
| 1 lượt | 100 LN |
| 10 lượt | 900 LN (giảm 10%) |
| 50 lượt | 4,000 LN (giảm 20%) |

**Pool Thưởng:**

| Rarity | Tỉ Lệ | Ví Dụ Item |
|--------|--------|------------|
| ⬜ Common | 45% | 500-1,000 LS, Pet Food, Linh Dược |
| 🟢 Uncommon | 25% | 2,000-5,000 LS, Cường Hóa Thạch x5, Uncommon Pet |
| 🔵 Rare | 15% | 10,000 LS, Bảo Hộ Phù, Rare Pet, Skill Book |
| 🟣 Epic | 10% | 30,000 LS, Epic Pet, Epic Gear, Đột Phá Đan x5 |
| 🟡 Legendary | 4% | Legendary Pet, Legendary Gear, 100,000 LS |
| 🔴 **JACKPOT** | 1% | **Mythic Pet, Divine Gear, 500,000 LS** |

**Pity System (Bảo Đáy):**
- Mỗi 50 lượt không trúng Legendary+ → **Đảm bảo 1 Legendary**
- Mỗi 100 lượt không trúng Jackpot → **Đảm bảo 1 Jackpot**
- Counter **KHÔNG reset** khi đổi banner → Người chơi yên tâm quay

> **Tâm lý:** Pity system tạo cảm giác "gần trúng rồi, quay thêm" → Chi nhiều hơn dự định

#### 🎰 Thiên Cơ Luân Giới Hạn (Limited Banner — Mỗi 2 Tuần)

Mỗi 2 tuần có banner mới với **1 item Exclusive** không thể kiếm bằng cách khác:

| Banner Ví Dụ | Exclusive Item | Rate-Up |
|-------------|---------------|--------|
| "Long Vương Giáng Thế" | 🐉 Pet Long Vương Hoàng Kim | 50% khi trúng Legendary |
| "Kiếm Tiên Truyền Thuyết" | ⚔️ Huyền Thiên Kiếm (Mythic) | 50% khi trúng Jackpot |
| "Phượng Hoàng Niết Bàn" | 🔥 Phượng Hoàng Skin Set | 50% khi trúng Epic+ |

> **2 tuần rồi hết** → "Nếu không quay bây giờ, mất vĩnh viễn" → FOMO maximum

---

### 17.5 Shop Tiện Ích (Convenience Shop)

Bán bằng Linh Ngọc — mua shortcut, không bán sức mạnh trực tiếp:

| Item | Giá (LN) | Hiệu Ứng | Pain Point Giải Quyết |
|------|---------|-----------|----------------------|
| **Đột Phá Bảo Đan** | 200 | Đảm bảo đột phá 100% (không cần đánh boss) | Boss Thiên Kiếp quá khó |
| **Bảo Hộ Phù Cao Cấp** | 150 | Cường hóa thất bại → không giảm level | Mất +12 về +10 quá đau |
| **Dungeon Drop Phù** (2h) | 100 | Khôi phục drop rate 100% trong 2 giờ (bỏ qua diminishing) | Drop rate giảm sau 3 lần clear |
| **Tu Vi Đan x2** | 80 | Double EXP 2 giờ | Lên level quá chậm |
| **Linh Tuyền Cao Cấp** | 50 | Giảm 50% thời gian farm (thay vì 20%) | Chờ cây mọc lâu quá |
| **Mở Rộng Kho** | 300 | +1 row kho vĩnh viễn | Kho đầy không đủ chỗ |
| **Đổi Tên** | 100 | Đổi tên nhân vật 1 lần | Muốn đổi tên |
| **Hồi Sinh Phù** | 50 | Hồi sinh tại chỗ trong dungeon (thay vì restart) | Chết ở boss gần hết máu |
| **Pet Slot Mở Rộng** | 500 | +1 pet slot vĩnh viễn (max +3) | Thiếu chỗ mang pet |
| **Skill Reset Đan** | 200 | Reset toàn bộ skill point | Build sai muốn đổi |
| **Teleport Đan** | 30 | Teleport ngay đến bất kỳ warp | Lười đi bộ |
| **Auto-Loot Phù** (24h) | 80 | Tự động nhặt drop 24 giờ | Nhặt đồ mệt tay |
| **Tăng Tốc AFK** (24h) | 100 | AFK Tu Vi ×2 trong 24h | AFK EXP quá ít |

> **Chiến lược:** Tạo pain point TRƯỚC → bán giải pháp SAU
> Ví dụ: Cường hóa +10→+12 thất bại 3 lần (mất ~2 ngày cày) → Player rage → Thấy Bảo Hộ Phù 150 LN = "rẻ hơn cày lại"

---

### 17.6 Crate System — Rương Bảo Vật (ExcellentCrates)

Sử dụng plugin **ExcellentCrates** đã có trên server:

| Rương | Key Source | Thưởng Tốt Nhất | Mua Key Bằng LN |
|-------|-----------|-----------------|----------------|
| **Rương Đồng** | Daily login, quest | Uncommon gear | 30 LN/key |
| **Rương Bạc** | Weekly quest, dungeon | Rare gear, Pet Egg | 80 LN/key |
| **Rương Vàng** | Hard dungeon, event | Epic gear, Skill Book | 200 LN/key |
| **Rương Kim Cương** | Bí Cảnh, season reward | Legendary gear/pet | 500 LN/key |
| **Rương Tiên** | Chỉ bán bằng LN | Mythic gear, Divine chance | 1,000 LN/key |

> **Rương Tiên** = pure premium crate, best items → whale magnet
> **Key drop rate đủ để "nếm" nhưng không đủ để "no"** → Mua thêm key

---

### 17.7 Cosmetic Shop (Ngoại Hình)

Bán bằng Linh Ngọc — **KHÔNG ảnh hưởng gameplay**, nhưng flex status:

| Loại | Giá (LN) | Ví Dụ |
|------|---------|-------|
| **Skin Vũ Khí** | 300-800 | Kiếm Lửa Effect, Đao Băng Giá Effect |
| **Cánh (Wings)** | 500-1,500 | Thiên Thần, Ác Quỷ, Phượng Hoàng |
| **Aura/Particle** | 200-600 | Hỏa Diễm, Lôi Điện, Linh Khí xoáy |
| **Trail (Đường Đi)** | 150-400 | Hoa Rơi, Linh Quang, Sao Băng |
| **Chat Color/Format** | 100-300 | Gradient tên, Bold chat, Emoji |
| **Island Theme** | 800-2,000 | Re-skin đảo: Tiên Giới, Ma Giới, Long Cung |
| **Pet Skin** | 300-500 | Skin mới cho pet hiện có |
| **Kill Effect** | 200-500 | Effect đặc biệt khi giết mob/player |
| **Danh Hiệu Custom** | 1,000 | Tự tạo danh hiệu riêng (có duyệt) |

> **Key insight:** Người chơi CHỊU CHI NHIỀU NHẤT cho cosmetic để flex trước bạn bè
> **Limited cosmetic** (mỗi season/event) → FOMO + exclusivity

---

### 17.8 Gói Khởi Đầu & Gói Giới Hạn (Starter & Limited Packs)

#### Gói Khởi Đầu (Mỗi Account Chỉ Mua 1 Lần)

| Gói | Giá (VNĐ) | Nội Dung | Giá Trị Thực |
|-----|-----------|----------|-------------|
| **Tân Thủ** | 20,000 | 200 LN + Uncommon Pet + 5,000 LS + 3 Crate Key | ~80K giá trị |
| **Tu Sĩ** | 50,000 | 600 LN + Rare Pet + 20,000 LS + Epic Key + Skin | ~200K giá trị |
| **Đại Năng** | 200,000 | 3,000 LN + Epic Pet + 100,000 LS + VIP2 + Legendary Key | ~800K giá trị |

> **"Giá trị gấp 4×"** → Tâm lý: "Không mua = lỗ" → Conversion rate rất cao
> **Chỉ mua 1 lần** → Urgency: "Nếu skip bây giờ, mất cơ hội tốt nhất"

#### Gói Giới Hạn (Flash Sale — Random, 24-48h)

| Gói | Giá | Trigger Xuất Hiện | Nội Dung |
|-----|-----|-------------------|----------|
| **Gói Cứu Trang Bị** | 50 LN | Sau khi cường hóa thất bại 3 lần | Bảo Hộ Phù x5 + Cường Hóa Thạch x20 |
| **Gói Sức Mạnh** | 100 LN | Sau khi thua dungeon 2 lần | Tu Vi Đan x2 + Hồi Sinh Phù x3 + Buff Pot |
| **Gói Pet Cứu Hộ** | 80 LN | Sau khi Pet Fusion thất bại | Pet Egg (guaranteed Rare+) + Pet Food x50 |
| **Gói Cày Hiệu Quả** | 60 LN | Sau khi drop rate giảm xuống 40% | Dungeon Drop Phù x3 + Double Drop 2h |
| **Gói Level Up** | 150 LN | Khi còn thiếu <10% EXP để lên level | Tu Vi Đan x3 + AFK Boost 24h |

> **Flash Sale xuất hiện ĐÚNG LÚC người chơi frustrated** → Conversion tối đa
> **Countdown timer** → "Còn 23:59:00" → Urgency

---

### 17.9 Hệ Thống Nạp Tích Lũy (Spending Milestone)

Càng nạp nhiều → càng nhiều thưởng bonus:

| Tổng Nạp Tích Lũy | Thưởng |
|-------------------|--------|
| 500 LN | Danh hiệu "Tiểu Thí Chủ" + 1,000 LS |
| 2,000 LN | Frame Avatar Bronze + Pet Egg Rare |
| 5,000 LN | Frame Avatar Silver + Legendary Key + 50,000 LS |
| 10,000 LN | Frame Avatar Gold + VIP3 (nếu chưa có) + Exclusive Skin |
| 25,000 LN | Frame Avatar Diamond + Mythic Pet Egg + 500,000 LS |
| 50,000 LN | 👑 Frame Avatar Crown + Danh Hiệu "Thần Hào" + Divine Weapon Box |

> **Tâm lý:** Người đã nạp 4,000 LN → "Chỉ thêm 1,000 nữa là được Silver frame" → Nạp thêm
> **Các milestone gần nhau ở đầu, xa dần ở sau** → Hook người mới, whale tự chạy

---

## 18. 🧠 Chiến Lược Hút Máu Tâm Lý

### 18.1 Pain Point → Solution Framework

Mỗi hệ thống gameplay được thiết kế với **điểm đau** có chủ đích:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    VÒNG LẶP HÚT MÁU                               │
│                                                                     │
│   Gameplay Fun ──► Hit Wall (Pain Point) ──► Frustration            │
│        ▲                                        │                   │
│        │                                        ▼                   │
│        │                              ┌── Cày (Free Path) ── Lâu    │
│        │                              │                             │
│        │                              └── Nạp (Paid Path) ── Nhanh  │
│        │                                        │                   │
│        └────────────────────────────────────────┘                   │
│                                                                     │
│   Key: Pain point phải ĐỦ ĐAU để muốn nạp,                       │
│        nhưng KHÔNG QUÁ ĐAU để bỏ game                             │
└─────────────────────────────────────────────────────────────────────┘
```

### 18.2 Bảng Pain Point Chi Tiết

| Hệ Thống | Pain Point (Điểm Đau) | Giải Pháp Free | Giải Pháp Trả Phí | Tỉ Lệ Nhanh Hơn |
|-----------|----------------------|---------------|-------------------|------------------|
| **Dungeon** | Drop rate giảm dần sau 3 lần/ngày | Chấp nhận drop thấp hoặc chờ mai | Dungeon Drop Phù / VIP bảo toàn | 100% drop rate vô hạn |
| **Cường Hóa** | Thất bại -2 level, mất 2 ngày cày | Cày lại nguyên liệu | Bảo Hộ Phù | 100% bảo toàn |
| **Đột Phá** | Boss Thiên Kiếp quá khó | Cày gear mạnh hơn | Đột Phá Bảo Đan | Skip boss |
| **Level** | Lên level chậm (mid-late game) | Grind thêm | Tu Vi Đan ×2, VIP EXP bonus | 2-3× nhanh hơn |
| **Pet** | Fusion thất bại 40%, mất 3 pet | Cày 3 pet mới | Pet Egg từ Gacha/Crate | Ngay lập tức |
| **Farm** | Cây mọc lâu (12-48h) | Chờ | Linh Tuyền Cao Cấp / VIP auto-collect | 50% nhanh hơn |
| **Kho** | Kho đầy, không đủ chỗ | Bán bớt | Mở Rộng Kho | Vĩnh viễn |
| **PVP** | Thua vì gear yếu | Cày gear free | VIP drop bonus → gear nhanh hơn | 2-5× |
| **Island** | Đảo nhỏ, thiếu plot | Cày LS upgrade | VIP Island bonus | +50% size |
| **Trade** | Thuế 5% mỗi giao dịch | Chấp nhận | VIP giảm → 0% thuế | 100% tiết kiệm |

### 18.3 Chiến Thuật Tâm Lý Cụ Thể

#### 💀 Sunk Cost Fallacy (Chi Phí Chìm)
```
Người chơi đã nạp 200K VNĐ:
├─ Có VIP2, Pet Epic, Gear +10
├─ Đang ở Kim Đan (giữa game)
├─ Suy nghĩ: "Đã đầu tư nhiều rồi, bỏ thì phí"
└─ Kết quả: Tiếp tục nạp thêm để "bảo vệ đầu tư"
```

#### 🔥 FOMO (Fear of Missing Out)
- Limited banner Gacha chỉ 2 tuần → "Bây giờ hoặc không bao giờ"
- Battle Pass exclusive skin → "Season này miss = mất vĩnh viễn"
- Flash Sale sau fail → "Giá tốt nhất, chỉ 24h"
- World Boss mỗi 12h → Login để không bỏ lỡ
- Event mùa chỉ 3 ngày → Phải chơi ngay

#### 👥 Social Pressure (Áp Lực Xã Hội)
- VIP prefix hiển thị trước tên → Ai cũng biết ai là VIP
- Cosmetic wings/aura → "Sao nó đẹp thế, mình cũng muốn"
- Island ranking → VIP đảo lên nhanh → Đảo free tụt hạng
- PVP → VIP gear tốt hơn → Free thua → Muốn mạnh hơn
- **Thông báo toàn server:** "🎉 [Player] vừa nhận được 🐉 Long Vương từ Thiên Cơ Luân!"

#### ⏰ Time Gate → Pay to Skip
- Dungeon drop giảm dần → Mua Dungeon Drop Phù / VIP bảo toàn
- Farm timer 48h → Mua tăng tốc  
- Đột phá cooldown 1h → Mua skip
- Cường hóa cần nguyên liệu → Mua trực tiếp
- **Nguyên tắc:** Mọi thứ cần THỜI GIAN đều có thể MUA bằng TIỀN

#### 🎰 Variable Ratio Reinforcement (Phần Thưởng Ngẫu Nhiên)
- Crate opening → "Lần này có thể trúng Legendary"
- Gacha pity system → "Còn 12 lượt nữa là guaranteed"
- Cường hóa RNG → "Lần sau chắc thành công"
- Dungeon rare drop → "Boss này có thể drop Mythic"
- **Hiệu ứng ánh sáng/âm thanh** khi trúng item hiếm → Dopamine rush

### 18.4 Luồng Hút Máu Theo Thời Gian Chơi

```
📅 NGÀY 1-3 (Newbie)
├─ Cho chơi miễn phí, rất fun, progress nhanh
├─ Hiển thị Gói Khởi Đầu Tân Thủ 20K ("Giá trị 80K!")
├─ Hiển thị Thẻ Tháng Bạc ("Chỉ 50K cho 30 ngày!")
└─ Target: First purchase, phá vỡ rào cản tâm lý nạp

📅 NGÀY 4-14 (Early)
├─ Hit wall đầu tiên: drop rate giảm sau 3 lần dungeon/ngày, lên level chậm
├─ Flash Sale "Gói Sức Mạnh" xuất hiện khi fail dungeon
├─ VIP1 quảng cáo: "+10% Tu Vi, +5% Drop Rate, 5 lần full drop"
├─ Battle Pass Season bắt đầu
└─ Target: VIP1 + Thẻ Tháng, tổng ~100-150K

📅 NGÀY 15-45 (Mid Game)
├─ Cường hóa bắt đầu thất bại nhiều → Bảo Hộ Phù
├─ Pet Fusion thất bại → Gacha Pet temptation
├─ VIP2-3 gap rõ ràng (thấy VIP3 fly, heal, particle)
├─ Limited Banner Gacha đầu tiên
└─ Target: VIP2-3 + Gacha + BP, tổng ~300-500K

📅 NGÀY 46-90 (Late Game)
├─ PVP gap: VIP gear mạnh hơn Free rõ ràng
├─ Island ranking: VIP đảo top, Free đảo tụt
├─ Sunk cost: "Đã nạp 500K rồi, thêm chút nữa"
├─ Nạp tích lũy gần milestone → "Thêm 1,000 LN nữa = Gold Frame"
└─ Target: VIP4 + ongoing spending, tổng ~1-2M

📅 NGÀY 90+ (Endgame / Whale)
├─ Min-max gear +15: cần Bảo Hộ Phù liên tục
├─ Collect all Legendary/Mythic Pet: Gacha heavy
├─ Season ranking chase: spend to stay competitive
├─ VIP5 = ultimate flex + 0% tax + unlimited dungeon
├─ Cosmetic collecting: mua hết skin mỗi season
└─ Target: VIP5 + whale spending, tổng 5M+
```

### 18.5 Doanh Thu Dự Kiến (Mô Hình)

| Phân Khúc | % Người Chơi | Chi Tiêu Trung Bình | Mô Tả |
|-----------|-------------|---------------------|--------|
| **Free Player** | 60% | 0 | Không nạp, cày grind |
| **Minnow** (Cá Nhỏ) | 20% | 50-200K VNĐ | Thẻ Tháng + Gói Khởi Đầu |
| **Dolphin** (Cá Heo) | 12% | 200K-1M VNĐ | VIP2-3 + BP + Gacha nhẹ |
| **Whale** (Cá Voi) | 5% | 1-5M VNĐ | VIP4-5 + Gacha nặng |
| **Mega Whale** | 3% | 5M+ VNĐ | Max everything, mọi cosmetic |

> [!TIP]
> **80/20 Rule:** ~80% doanh thu đến từ ~20% người chơi (Dolphin + Whale)
> **Focus:** Nuôi Free player để tạo ecosystem → Whale nạp để flex trước Free player

### 18.6 Thông Báo "Ai Đó Vừa Trúng" (Social Proof)

Hiển thị toàn server khi:
- 🎉 Ai đó quay trúng Legendary+ từ Gacha
- 🎉 Ai đó mở Rương Tiên trúng Mythic
- 🎉 Ai đó cường hóa +13, +14, +15 thành công
- 🎉 Ai đó đột phá Tiên Nhân
- 🎉 Ai đó mua VIP5

> **Mục đích:** "Người khác trúng được, mình cũng có thể" → Kích thích quay/mở thêm

---

> [!TIP]
> ### Ưu Tiên Phát Triển
> 1. **Phase 1**: Skyblock core + Tu Luyện + Level + Dungeon T1-4
> 2. **Phase 2**: MMOItems gear + Pet system + Dungeon T5-7
> 3. **Phase 3**: Skills + Quest + Trade + Dungeon T8-10
> 4. **Phase 4**: PVP + Events + Bí Cảnh + Polish
> 5. **Phase 5**: VIP System + Gacha + Battle Pass + Monetization

---

*Document Version: 2.0 — Skyblock Tu Tiên Game Design + Monetization*
*Created: 15/04/2026*
*Updated: 15/04/2026 — Thêm hệ thống hút máu toàn diện*
