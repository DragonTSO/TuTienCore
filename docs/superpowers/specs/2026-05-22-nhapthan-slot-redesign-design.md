# TuTienCore Nhap Than Slot Redesign

Date: 2026-05-22
Status: Draft for user review
Scope: Replace old infusion flow with inventory + one-slot equip model.

## 0. Architecture Boundaries

This spec keeps implementation minimal but with clear responsibility boundaries:

1. `NhapThanCommand`: argument parse, permission gate, sender feedback.
2. `InfusionService` (can stay inside `InfusionManager` in this phase): equip/unequip transaction rules.
3. `InfusionRepository` (implemented via `PlayerDataManager`): load, save, migrate, sanitize player infusion state.
4. `InfusionEffectApplier`: apply and clear runtime effects (MythicLib stats and TuLuyen bonus).
5. `InfusionGuiController` (can stay inside `InfusionManager`): page mapping and click handling.

No extra subsystem beyond these boundaries is in scope.

Concrete ownership in this phase:

1. `NhapThanCommand` owns all `/nhapthan give` parse and permission checks.
2. `InfusionManager` owns GUI event routing and calls into service/repository methods.
3. `PlayerDataManager` owns persistence, migration, sanitization, and cache truth.
4. `InfusionEffectApplier` owns only runtime effect changes (no persistence).

## 1. Goals

1. Remove old first-claim random flow from `/nhapthan`.
2. Keep `rarity` as part of Nhap Than power model.
3. Replace old 6 types with 10 new types.
4. Add admin grant command: `/nhapthan give <player> <type> <rarity>`.
5. Player can own multiple Nhap Than entries in storage.
6. Player can equip exactly 1 Nhap Than at any time.
7. Buffs apply only from equipped Nhap Than.

## 2. Non-goals

1. No TuTienForge bonus integration in this phase.
2. No pity or weighted gacha in this phase.
3. No multi-slot equip in this phase.
4. No cross-plugin migration tool beyond local data migration in PlayerData.

## 3. High-level Behavior

1. `/nhapthan` opens management GUI.
2. GUI has one equipped slot and a storage grid.
3. Clicking a storage item equips it.
4. If another item is equipped, old one is unequipped first.
5. Clicking equipped slot unequips the current item back to storage.
6. `/nhapthan give <player> <type> <rarity>` adds one entry to storage only.

## 4. Data Model

### 4.1 OwnedInfusion (existing class, expanded)

Fields:

1. `id` (UUID-string, unique per owned entry)
2. `type` (InfusionType)
3. `rarity` (InfusionRarity)
4. `createdAt` (long)

Reason:

1. `id` prevents ambiguity when multiple entries share same `type+rarity`.

### 4.2 PlayerDataManager infusion state

Replace single owned cache with:

1. `Map<UUID, List<OwnedInfusion>> infusionInventoryCache`
2. `Map<UUID, String> equippedInfusionIdCache`

Persisted YAML shape per player:

```yaml
<uuid>:
  infusion:
    inventory:
      - id: "..."
        type: "NAM_MINH_LY_HOA"
        rarity: "COMMON"
        created-at: 1716400000000
    equipped-id: "..." # nullable / absent if none equipped
```

## 5. Command Design

### 5.1 `/nhapthan`

1. Player-only command.
2. No-arg: open GUI.

### 5.2 `/nhapthan give <player> <type> <rarity>`

1. Permission node: `tutiencore.nhapthan.give` (default: op).
2. Command is executable from both player and console.
3. Target forms accepted:
   - online exact name
   - exact UUID string
   - exact cached offline name only if `hasPlayedBefore=true`
4. Never-joined or not-cached offline targets are rejected with `messages.give-player-not-found`.
5. Resolution order is deterministic:
   - `Bukkit.getPlayerExact(name)`
   - parse UUID and resolve offline player by UUID
   - `Bukkit.getOfflinePlayerIfCached(name)` and require non-null + `hasPlayedBefore=true`
6. No remote name lookup is performed in this phase.
7. Validate `type` enum.
8. Validate `rarity` enum.
9. Create owned entry with new `id` and `createdAt`.
10. Append to inventory.
11. Do not auto-equip.
12. If target online and GUI open, refresh inventory view.
13. Sender feedback keys:
   - no permission: `messages.no-permission`
   - invalid usage: `messages.give-usage`
   - invalid type: `messages.give-invalid-type`
   - invalid rarity: `messages.give-invalid-rarity`
   - success: `messages.give-success`

## 6. GUI Design

Layout rules (fixed 27-slot menu):

1. One fixed equipped slot (center).
2. One storage area for owned entries.
3. Fill unused cells with neutral pane.
4. Storage capacity per page: 18 entries.
5. Overflow rule: paginated view with `prev` and `next` buttons.
6. Page index is ephemeral UI state (not persisted).
7. Max owned entries: 270.
8. If inventory is full, `give` fails with `messages.inventory-full`.
9. If legacy/manual data load contains more than 270 entries, player may keep existing entries but cannot receive more until count drops below 270.

Slot positions are fixed in code for this phase (not configurable).

Slot map:

1. Equipped slot: `13`.
2. Storage slots: `0-8` and `18-26`.
3. Prev page button: `9`.
4. Next page button: `17`.
5. Info/close utility slots: `10` and `16`.

Interactions:

1. Click storage item: equip selected entry.
2. Click equipped item: unequip.
3. On equip success: reapply infusion modifiers.
4. On unequip success: remove infusion modifiers.
5. If equip/unequip fails, keep GUI state unchanged and show error message.
6. All click types in this GUI are cancelled to prevent item movement:
   - normal click
   - shift-click
   - number-key swap
   - drag
   - double-click collect

Display:

1. Item name from type display config.
2. Lore includes rarity and stat preview.
3. Equipped item has clear marker in lore/title.

## 7. Buff Application Rules

1. Effective stats formula per stat key:

   `effective = roundHalfUp(typeBase * rarityMultiplier, 4)`

   Java contract:
   `BigDecimal.valueOf(typeBase).multiply(BigDecimal.valueOf(rarityMultiplier)).setScale(4, RoundingMode.HALF_UP).doubleValue()`

   Final value submitted to MythicLib keeps 4 decimal places.
2. All applied modifiers use existing prefix: `tutien_infusion_`.
3. Before applying new set, remove all existing infusion modifiers for player.
4. On player join/reload, reapply only equipped entry.
5. If a stat key exists in type but missing in MythicLib mapping, skip key and log one warning per server boot or `/ttc reload` per `(type, rarity, statKey)`.
6. If type or rarity config entry is missing for equipped item, clear equipped state and remove modifiers.

Warning dedupe implementation:

1. Keep in-memory `Set<String>` warning keys.
2. Key format: `type|rarity|statKey`.
3. Clear set on plugin enable and on infusion reload.

Equip transaction contract:

1. Validate selected owned entry exists.
2. Snapshot current equipped entry id.
3. Clear active infusion modifiers.
4. Apply selected entry modifiers.
5. If apply succeeds:
   - persist new `equipped-id`
   - save player data
6. If apply fails:
   - clear partial modifiers
   - restore previous equipped entry modifiers if previous exists and config valid
   - keep persisted `equipped-id` unchanged
   - return failure message to player
7. If apply succeeds but save fails:
   - clear new modifiers
   - restore previous equipped modifiers if previous exists and config valid
   - keep previous `equipped-id` in memory and storage
   - log error with player UUID
   - return failure message to player

TuLuyen bonus:

1. Bonus is configured by type and scaled by rarity.
2. Introduce an internal boundary interface:
   - `InfusionEffectApplier` with methods:
     - `apply(UUID playerId, OwnedInfusion ownedInfusion)`
     - `clear(UUID playerId)`
   - contract:
     - both methods run on main thread
     - `apply` is all-or-fail via thrown exception
     - `clear` is best-effort and should not throw for missing modifiers
3. First implementation: one concrete applier can handle both MythicLib stats and TuLuyen bonus in this phase.
4. Keep TuLuyen-specific math isolated inside applier so future TuTienForge support can be added without changing command/GUI code.

## 8. Config Structure

File: `nhapthan/infusion.yml`

Sections:

1. `rarities`: multiplier + display.
2. `types`: 10 type definitions.
3. `gui`: title and item templates only.
4. `messages`: feedback strings.

Required `messages` keys:

1. `no-permission`
2. `give-usage`
3. `give-player-not-found`
4. `give-invalid-type`
5. `give-invalid-rarity`
6. `give-success`
7. `inventory-full`
8. `item-no-longer-available`
9. `equip-failed`
10. `unequip-failed`
11. `inventory-recovered-empty`
12. `feature-disabled`

Required `gui` keys:

1. `title`
2. `item-equipped`
3. `item-storage`
4. `item-prev-page`
5. `item-next-page`
6. `item-close`
7. `item-info`

Fallback rule:

1. Missing required `messages`/`gui` keys use built-in defaults and log warning.

Type list to replace old 6 entries:

1. `NAM_MINH_LY_HOA`
2. `THAI_DUONG_CHAN_HOA`
3. `TU_VI_THIEN_HOA`
4. `CUU_THIEN_HUYEN_HOA`
5. `U_MINH_QUY_HOA`
6. `THAI_SO_THANH_HOA`
7. `U_LA_BANG_VIEM`
8. `CUU_TU_LUAN_HOI_VIEM`
9. `BICH_HAI_THANH_THIEN_VIEM`
10. `HONG_LIEN_THUC_COT_VIEM`

## 9. Migration Plan

When loading player data:

1. Detect legacy keys:
   - `infusion.claimed`
   - `infusion.type`
   - `infusion.rarity`
   - `infusion.created-at`
2. If legacy exists and new `inventory` missing:
   - create one inventory entry from legacy values
   - set `equipped-id` to that new entry id
3. Keep legacy keys untouched in first release for rollback safety.
4. Optional cleanup of legacy keys can be done in later release.

Mixed schema rule:

1. If new `inventory` exists and has at least one valid entry, legacy keys are ignored.
2. If new `inventory` missing or sanitizes to empty and legacy is valid, migrate one legacy entry.
3. After migration, runtime writes only new schema fields. Legacy fields remain read-only for rollback visibility.

Corruption recovery rules during load:

1. Duplicate owned `id` values:
   - keep first occurrence
   - regenerate new UUID-string ids for later duplicates
2. Malformed inventory entries (missing type/rarity or enum parse failure):
   - drop entry
   - log one concise warning with player UUID
3. Legacy enum parse failure during migration:
   - do not migrate legacy entry
   - log one concise warning with player UUID
4. `equipped-id` not found in sanitized inventory:
   - auto-clear equipped state
5. Inventory node not a list:
   - treat as empty list
   - attempt legacy migration if legacy fields exist
6. If sanitization drops all entries from a non-empty raw inventory:
   - keep final inventory empty
   - log warning with dropped count and player UUID
   - if player is online during load, send `messages.inventory-recovered-empty`

## 10. Failure Handling

1. Invalid command arity/shape -> `messages.give-usage`.
2. Invalid type -> `messages.give-invalid-type`.
3. Invalid rarity -> `messages.give-invalid-rarity`.
4. Missing equipped id in inventory -> auto-clear equipped state.
5. Fatal config error (YAML unreadable, section root missing) -> disable Nhap Than feature and log error.
6. Modifier apply failure -> remove partial modifiers and report once per server boot or `/ttc reload` per `(playerUuid, type, rarity, causeClass)`.
7. Pagination overflow or invalid page index -> clamp page into valid range.
8. GUI stale state (entry deleted while menu open) -> refresh page and show `messages.item-no-longer-available`.
9. Recoverable config error (single missing type/rarity/stat mapping) -> skip bad entry only; feature remains enabled.

Feature disabled behavior:

1. If Nhap Than feature is disabled by fatal config error:
   - `/nhapthan` returns `messages.feature-disabled`.
   - `/nhapthan give ...` returns `messages.feature-disabled`.
   - no data mutation is performed.

## 11. Testing Plan

Unit tests:

1. Parse type and rarity from command args.
2. Inventory add/remove/equip transitions.
3. Legacy data migration into new schema.
4. Modifier reapply behavior on equip switch.
5. Equip rollback when apply fails.
6. Sanitization for duplicate ids and malformed entries.
7. Command target parsing for online, offline-played, and never-joined players.
8. Config fallback defaults for missing `messages` and `gui` keys.
9. Fatal config disable path for unreadable or root-missing YAML.

Integration tests (MockBukkit or current test style):

1. `/nhapthan` opens GUI for player.
2. `/nhapthan give` inserts storage entry.
3. Equip then relog keeps same equipped entry and buffs.
4. GUI click cancellation for shift/drag/number-key.
5. Pagination boundaries first/last page.
6. Save-failure rollback after successful apply path.

Manual verification on server:

1. Give 2 entries to same player.
2. Equip A -> check stat change.
3. Equip B -> A removed, B applied.
4. Unequip -> all infusion modifiers removed.
5. Reload plugin -> equipped entry reapplies.
6. Force bad type config for one entry -> only that entry skipped.

## 12. Rollout Steps

1. Implement model and storage schema updates.
2. Implement migration in PlayerDataManager load path.
3. Refactor InfusionManager GUI and equip logic.
4. Extend NhapThanCommand with `give` subcommand.
5. Update config and enum list to 10 types.
6. Run tests and build.
7. Deploy jar to server and restart.
8. Run live data validation command/log check for migrated players (inventory count, equipped-id integrity).
9. Verify runtime behavior with admin command + GUI flow.

## 13. Final Decisions

1. Replace old types with new 10 types: Yes.
2. Keep rarity model: Yes.
3. `give` rarity handling: explicit arg required.
4. `give` auto-equip: No.
