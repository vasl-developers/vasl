# Release Notes: Version 6.7.0 

**Release Date:** April 2nd 2025

---

## 🚀 New Features
 - Feature 1: Top concealment counter can now be made transparent for owner of non-dummy stack. This feature can be turned on/off in preference Tab. Removed Question Mark (?) from top concealment counters when they are being drawn with reduced opacity so it easier to see the counter(s) behind them.
 - Feature 2: When a special ammo is selected as depleted, it automatically displays below the counter.
 - Feature 3: Allow players to change the popup menu of a gamepiece when righ-clicking on a menu itme.This sends the menu item to an "unwanted" submenu.
 - Feature 4: Implementation of autosave feature. Creates save files every 10 minutes in users' "boards" directory. Maximum of 20 files created. Can be turned on/off in Preferences.

---

## 🛠 Bug Fixes
- Fix 1: Add BH & HC options to HOB menu for US Army 447 and 336 Squads and HS.
- Fix 2: Remove unneeded traits from White Gun CA (Field of Fire) counters.
- Fix 3: Fixed bug where concealed broken SW was being revealed by the Broken/Malf SW/Gun report.
- Fix 4: Fixed bug where ? counter was drawn with reduced opacity when on top of level counter.
- Fix 5: Fixed Depleted Ammo display on German 37L AT Gun.
- Fix 6: Fixed LOS bug with respect to on-map bridges so LOS can be drawn to bridge or depression level.
- Fix 7: Fixed bug that causes game to crash when labels on counters use parenthesis.
- Fix 8: Fixed LOS bugs on boards LFT3-10 caused by overlays and terrain transformations.
- Fix 9: Fixed LOS bugs when using Gutted Factory overlays on boards RB and RO, and when applying Dense Jungle terrain transformation.
_ Fix 10: Fixed bug when a language other than English is selected in Vassal Preferences. This was causing errors when selecting boards.
- Fix 11: Fixed issue for linux devices when using multiple windows and counters are not painted when entering new window.

---

## 📈 Improvements
- Improvement 1: Changed implementation of concealment counters to try and take into consideration multiple locations with a hex denoted by counters (e.g. building level counters).
- Improvement 2: Broken guns/sw are only highlighted to the owners.
- Improvement 3: Draw HIP units a bit offset when enemy enters hex so they remain visible to owner.
- Improvement 4: Implemented SVG graphics for all infantry units, guns and vehicles, Boats, Planes, LC and Shared white vehicles and many information counters. New counters drawn from the palette will use SVG. To change counters from games saved in previous versions to SVG, use the Update Game funciton on ASL dropdown (potato masher icon) (NOTE: the Update Game function has been disabled in 6.7.0 due to a bug and will be restored in futre versions).
- Improvement 5: Extended LOS Checking to additional draggable Overlays: FFE, SMOKE, Rubble, OG, Wall/Hedge, Roads and Bridges.
- Improvement 6: Removed deluxe hex size option, made redundant by Board Zoomer.
- Improvement 7: Reordered the boards when selecting a map from BoardPicker and added a search box feature.
- Improvement 8: Added a number of SSR overlays to board files to implement terrain transformations for "U" scenarios.
- 

## ⚠️ Compatibility Notes
- **Minimum Version Required:** Built with VASSAL 3.7.15
- Compatible with games saved in versions 6.6.6–6.6.9.


---

## 📋 Additional Notes
- Use the Game Updater tool to convert games saved with older versions. This will update counter graphics to the svg versions in Beta6.

