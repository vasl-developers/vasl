# Release Notes: Version 6.7.0-beta1 

**Release Date:** Nov 19th 2024 

---

## 🚀 New Features
- Feature 1: Top concealment counter can now be made transparent for owner of non-dummy stack 
             This feature can be turned on/off in preference tab
- Feature 2: When a special ammo is selected as depleted, it automatically displays below counter
- Feature 3: Implementation of autosave feature. Creates save files every 10 minutes in users "board" directory. 
              Maximum of 20 files created. Can be turned on/off in preferences tab.
- Feature 4: Allow players to change the popup menu of a gamepiece when right clicking on a menu item. This sends the 
             menu item to an unwanted submenu. 



---

## 🛠 Bug Fixes
- Fix 1: Minor fix to the LOS check function so that checking LOS to a depression hex containing 
  multiple center locations is accurate
- Fix 2: Fixed bug where concealed broken sw was being revealed by the report broken/mal sw/gun button

---

## 📈 Improvements
- Improvement 1: Removed deluxe hex size option, made redundant by Board Zoomer
- Improvement 2: Reordered the boards when selecing a map and added a searchbox feature
- Improvement 3: Changed implementation of concealment counters. Now we try to take into consideration multiple locations within a hex denoted by counters (e.g. building levels)


---

## ⚠️ Compatibility Notes
- **Minimum Version Required:** Built with VASSAL 3.7.15
- Compatible with games saved in versions 6.6.6–6.6.8.


---

## 📋 Additional Notes
- Use the Game Updater tool to convert games saved with older versions.

