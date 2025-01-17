# Release Notes: Version 6.7.0-beta2 

**Release Date:** Nov 19th 2024 

---

## 🚀 New Features




---

## 🛠 Bug Fixes
- Fix 1: Minor fix to the LOS check function so that checking LOS to a depression hex containing multiple center locations is accurate
- Fix 2: Fixed los bug with respect to on-map bridges so LOS can be drawn to bridge or depression level 
- Fix 3: Fix bug that causes game to crash when labels on counters use parenthesis.
- Fix 4: Fixed bug when a language other than english is selected in vassal preferences. Previously this was causing errors when selecting boards.
- Fix 5: Fixed bug where ? counter was drawn with reduced opacity when on top of level counter.
- Fix 6: Fixes issue in linux when using multiple windows and counters are not painted when entering new window

---

## 📈 Improvements
- Improvement 1: Extended los checking to additional Draggable Overlays: FFE, SMOKE, Rubble, OG, Wall/Hedge, Road, and Bridges.
- Improvement 2: Minor tweak to ASLBrokenFinder so that broken guns/sw are only highlighted to the owners.
- Improvement 3: Removed Question mark from concealment counters when they are being drawn with reduced opacity so it is easier to see the counter behind them.
- Improvement 4: Draw HIP units a bit offset when enemy enters hex so they remain visible to owner

---

## ⚠️ Compatibility Notes
- **Minimum Version Required:** Built with VASSAL 3.7.15
- Compatible with games saved in versions 6.6.6–6.6.9.


---

## 📋 Additional Notes
- Use the Game Updater tool to convert games saved with older versions.

