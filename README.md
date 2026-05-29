# Bro Ledger

**Bro Ledger** is a read-only Java/JavaFX companion tool for _Battle Brothers_ that parses your save file and displays a live roster of your mercenaries during gameplay. It never writes to the save file.

> The save-file parsing logic and game-asset tooling in this repository originate from the [BB-Edit](https://github.com/scarglamour/bb-edit) Electron save editor by 9thKingOfLies and ScarGlamour. Without that base, this app is nothing.

---

## Features

### Savefile-extracted data

- Displays the full brother roster parsed from a `.sav` file
- **Auto-refreshes** when the save file is overwritten (from inside the game)

### Brother overview

The overview table shows all brothers. They can be sorted according to roles, and all stats, equipped items, traits and perks are shown in addition to several enhancements.

The different enhancements are as follows
- Weapon tier (on/off) shows a colored dot indicating the weapon tier (1/2/3/named). I found no community standard, so it's down to developer judgement.
- Armor stats is either off or shows either
  - durability 
  - fatigue cost
- Perk sorting can be either turned off or sort Bro perks by (alphabetical sorting is always final tiebreaker)
  - tier
  - commonality (those you've selected most often among current Bros is shown to the left)
  - tier, then commonality
- Stat values can be shown as current values only (talent stars are always shown above), or
  - current plus _potential_ max stats (see below)
  - _potential_ stats only
  - current plus _expected_ lvl 11 stats
  - _expected_ lvl 11 stats only 

Note that you can reorder stat and equipment columns (within their respective category). Most importantly, reordering the stat columns will also impact how they're listed in the brother detail view.

#### Potential stats

Potential stats are simply the current stat, plus number of remaining level-ups times the average roll, adjusted for talent stars (a Melee skill roll is 2 without stars, 2.5 with one etc).

Rounding will round halves up.

#### Expected stats

Expected stats is a best guess of final stats depending on where you allocate your stat increases up until level 11. This is done from the Brother detail card. When expected is active, and the increases have been allotted, you can mouseover the level cell and get level-up recommendations on which stats to increase based on rolls.

For post-level 11 Bros, you can instead assign where those level-up +1s went, to get "expected" to correspond to a lvl 11 stat line making it comparable to other Bros.

See **Bro developer** below. 

### Brother detail view

Each brother card shows:

- **Portrait**, name, title, and background with level (e.g. "Sellsword, level 4")
- **Traits and injuries** — icons with name tooltips
- **Stats** — Health, Resolve, Fatigue, Initiative, Melee Skill, Ranged Skill, Melee Defense, Ranged Defense; each with talent stars
  - Values reflect actual in-game numbers (base stat modified by traits and perks)
  - Hover a modified stat to see a breakdown: base value + each trait/perk contributor
  - **Potential** column shows expected stat range at level 11 based on current level and talent stars
  - **Expected** column shows a projected final stat based on your planned level-up allocation (see Bro Developer below)
- **Perks** — all 50 perks shown at full opacity (owned) or 30% opacity (unowned), with name tooltips
- **Equipment** — all 7 slots (weapon, shield, body, helmet, trinket, quiver, pouch) with stat tooltips
- **Derived stats footer** — effective armor value, total fatigue penalty, weapon damage range

### Brother annotations (persisted per save file)

Annotations are stored Bro data specific to BB keeper keyed by a brother's **fingerprint** — which is a hash of his name, background, and talent stars — so renaming a brother in-game will lose his annotations (changing his title is fine, though). Stars and background are fixed in normal gameplay and only change via an external save editor. The following are stored in a separate file which is placed next to the savefile (in same directory): `<save>.keeper.json`

- **Role** — the assigned role, editable from the overview or the brother card
- **Stat increases** — planned level-up increase allocations per stat (levels 1–11), see Bro Developer below
- **Post-11 increases** — per-stat increases already taken after level 11

Sort order (if you've drag-n-dropped brothers individually) and the state of the four overview enhancement buttons (Weapon Tier, Armor Stats, Perk Sorting, Stat values) are also stored in this way.

### Role System

Roles are user-defined categories applied to brothers to track roster composition and guide leveling.

- Create, rename, edit, delete, and reorder roles in a dedicated Role Manager window
- Role order is reflected in the Show All / Frontline / Backline preset buttons on the brother card
- Each role has a **frontline / backline** designation that influences sort order in the overview
- Each role can set a **target threshold** and **priority** (P1/P2/P3) per stat:
  - P1 targets are shown **bold**
  - P2 targets use normal weight
  - P3 targets use light italic
- Roles are identified by a stable UUID — renaming a role updates all brothers that have it already
- Role data is shared across all save files and stored in `~/.bro-ledger/roles.json`

### Bro Developer (Expected Stats)

Plan a brother's level-up path and see realistic projected final values:

- Each stat row in the brother card has a **`[− count +]` increase editor** and an **Expected** column
- A **remainder counter** tracks how many of the `3 × remainingLevels` total increases are unassigned
- **Auto-assign by role**: fills P1 stats first (equal split, alphabetical tiebreaker if >3 P1 stats), then cascades leftover increases to P2, then P3
- **Preferences** (toolbar button): switch between **Naive** mode (always available, updates live) and **Greedy** mode (optimal allocation, requires a fully-assigned brother; falls back to Naive if not complete); also controls level-up modal behaviour (see above)
  - For Naive mode: Expected value = `ceil(currentBase + mean_gain × allottedCount)`, then trait/perk modifiers applied
  - For Greedy mode: Expected value is based on an exploratory algorithm that takes into account the fact that if two stats should be leveled, you'd generally pick the one with the higher roll for that level.

Increases are persisted along with the other per save.

### Level-up notification

When the save file auto-refreshes and the app detects that a brother leveled up in-game, a **level-up summary modal** appears listing each brother who leveled, the stat deltas recorded, any perks added, and how many planned stat increases were consumed from the annotation. This keeps the planned allocation in sync without manual adjustment.

The modal behaviour is configurable in **Preferences**:

- **Modal** (default) — stays open until dismissed
- **Auto-close** — closes automatically after 15 seconds
- **Off** — suppresses the modal entirely

### Role Fit Comparison

The brother card supports side-by-side role comparison without leaving the card:

- The **persistent role column** always shows targets for the brother's assigned role
- A **placeholder column** is always visible to the right — select a role in its dropdown to add a comparison column; a new placeholder appears automatically
- Preset buttons populate all visible columns at once: **Show All**, **Frontline**, **Backline**, **Reset**
- The number of columns shown is limited to what fits the window width
- Target values are color-coded (green/yellow/red for met/potentially met/unmet) and styled by priority (bold / normal / light italic for P1/P2/P3)

---

## Prerequisites

- Java 25+
- JavaFX 25 ([gluonhq.com/products/javafx](https://gluonhq.com/products/javafx))
- Maven 3.9+ (the `.\mvnw` wrapper is included — no separate install needed)

---

## Setup

### 1. Run in development mode

```powershell
.\mvnw javafx:run        # Windows
./mvnw javafx:run        # macOS / Linux
```

On first launch, Bro Ledger shows a **Setup** dialog that extracts game assets directly from your Battle Brothers installation. Point it at `data/data_001.dat` inside your game folder, choose an output directory (defaults to `~/game-art`), and click **Extract**. This is required before the app will display portraits, item icons, and trait images. The `game-art/` folder is gitignored and populated by the in-app extractor.

If you have already extracted assets previously, click **Use existing folder** in the Setup dialog to point the app at the existing directory without re-extracting.

### 3. Build a standalone JAR

```powershell
.\mvnw package           # Windows
./mvnw package           # macOS / Linux
```

Output: `target/bro-ledger-1.0.0.jar`

Run the JAR with:

```bash
java --module-path $JAVAFX_HOME/lib --add-modules javafx.controls,javafx.fxml -jar target/bro-ledger-1.0.0.jar
```

---

## Running Tests

Unit tests (no save file needed):

```powershell
.\mvnw test
```

Parser integration test (requires a real `.sav` file):

```powershell
.\mvnw test -Dsav.file=path\to\your\save.sav    # Windows
./mvnw test -Dsav.file=path/to/your/save.sav    # macOS / Linux
```

---

## License

See LICENSE file.

MIT © 9thKingOfLies, ScarGlamour
