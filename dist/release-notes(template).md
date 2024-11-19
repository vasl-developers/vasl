This file is kept as a template for creating release notes for VASL modules. The template is used to generate 
the release notes for each new version of the module. The template includes sections for new features, 
bug fixes, improvements, compatibility notes, important changes, and additional notes. 


It can be freely modified and no strict requirements are enforced, however, when creating a pull request,
the workflow will check to see if a file named `release-notes.md` exists in the `dist` directory. This file will be 
checked to make sure it has the proper version number that matches the pom.xml version number. 


-------------------------------------------------------------------------
<!-- Replace {{version}} with actual version number -->
# Release Notes: Version {{version}}
<!-- If {{release_date}} is not entered, it will be generated automatically -->
**Release Date:** {{release_date}}

---

## 🚀 New Features
- Feature 1: Describe the new feature here.
- Feature 2: Describe another new feature here.

---

## 🛠 Bug Fixes
- Fix 1: Details about the bug that was fixed.
- Fix 2: Details about another bug fix.

---

## 📈 Improvements
- Improvement 1: Description of performance or usability improvement.
- Improvement 2: Additional improvements included in the release.

---

## ⚠️ Compatibility Notes
- **Minimum Version Required:** VASSAL 3.7.14
- Compatible with games saved in versions 6.6.6–6.6.8.

---

## 🔄 Important Changes
- Change 1: Details about a significant change or update.
- Change 2: Notes about another major change in this release.


---

## 📋 Additional Notes
- Use the Game Updater tool to convert games saved with older versions.
- Ensure compatibility with players using the same version for multiplayer games.
