# vasl-setup
Scripts to create Patrick Ireland's standard VASL directory structures, and download VASL vmod, enhanced scenarios, boards, and extensions

**These instructions assume you have already installed Java and the VASSAL engine**

* Jump to [On Windows](#on-windows)
* Jump to [On Mac OS X](#on-mac-os-x)
* Jump to [On Linux](#on-linux)

## On Windows
These instructions were verified on VASSAL 3.2.15 and VASL 6.2.2

### Download the script
1. Right-click on the link [vasl_setup.bat](https://raw.githubusercontent.com/vasl-devleopers/vasl/develop/install/vasl_setup.bat), and chose "Save as..." the file to your Downloads folder

### Update the script
**If you want to use the default ASL directory location and VASL version, skip this step and move on to "Run the script"**

**Currently, the Enhanced Scenarios have been saved with VASL 6.2.2, so if you use a later version of VASL,
you'll get a warning message about needing to convert the scenarios to the newer version**

1. Run WordPad (Hit Windows-R, then type in "WordPad" in the "Open:" text field, and press Enter)
2. In the "Document - WordPad" window, choose the "File > Open..." menu option
3. Navigate to your Downloads folder
4. In the lower right corner of the "Open" dialog, click the "All Wordpad Documents (*.rtf,)" option, and choose "All Documents (*.*)"
5. Click on the "vasl_setup.bat" file
6. Click the "Open" button
7. If you like, update the value for ASLROOT to wherever you want to setup the ASL files
8. If you like, update the value for VASLVERSION to the 3 digit version (important, don't use periods in the version number) of VASL you want to download (e.g. "630")
9. In the "vasl_setup.bat - WordPad" window, choose the "File > Save" menu option
10. Close WordPad

### Run the script

The script defaults are:

* ASLROOT=C:\ASL
* VASLVERSION=622

**WARNING: This script deletes the following directories in the ASL structure**

* %ASLROOT%\Scenarios\scenarios-master
* %ASLROOT%\VASL\vasl-boards-extensions-master

**WARNING: This script recreates the following directories in the ASL structure**

* %ASLROOT%\Scenarios\MMP\StarterKit\Enhanced
* %ASLROOT%\VASL\boards
* %ASLROOT%\VASL\extensions
* %ASLROOT%\VASL\extensions-6.0
* %ASLROOT%\VASL\extensions-common
* %ASLROOT%\VASL\extensions-complete
* %ASLROOT%\VASL\extensions-empty

**WARNING: If you care about any of these directories, back them up before running the script**

1. Open Windows Explorer, and navigate to your Downloads folder
2. Double-click "vasl_setup.bat"
3. If you get a warning saying "Windows protected your PC" regarding an "unrecognized app", click "More info",
and then click the "Run anyway" button
4. The script will finish in about a minute, depending on your Internet connection speed

### Setup VASL module and preferences
1. Launch VASSAL
2. In the "VASSAL" window, choose the "File > Open Module..." menu option
3. Navigate to "C:\ASL\VASL"
4. Click the "vasl-X.Y.Z.vmod" where X.Y.Z is the version (e.g. "vasl-6.2.2.vmod")
5. Click the "Open" button
6. A "Processing Image Tiles" window will pop up and show some processing.  It will close that window and launch VASL "Welcome" dialog when it's done
  * NOTE: The Welcome dialog may be behind other applications, like your web browser, so you may need to minimize applications until you see it. 
7. Click the "Cancel" button
8. In the "VASL controls" window, chose the "File > Preferences..." menu option
9. In the "Preferences" window, in the "General" tab, click the "Select" button next to "Board Directory"
10. Navigate to "C:\ASL\VASL\boards"
11. Click the "Open" button
12. In the "Preferences" window, in the "Extensions" tab, click the "Select" button next to "Extensions Directory"
13. Navigate to "C:\ASL\VASL\extensions-common"
14. Click the "Open" button
15. In the "Preferences" window, Click the "OK" button

### Test VASL setup by loading an Enhanced Scenario
1. In the "VASL controls" window, chose the "File > Load Game..." menu option
2. Navigate to "C:\ASL\Scenarios\MMP\StarterKit\Enhanced"
3. Click on the "S 001 Retaking Vierville - Enhanced.vsav" scenario file
4. Click the "Open" button
5. The scenario should load just fine with no error messages in the "VASL controls" window

## On Mac OS X
These instructions were verified on VASSAL 3.2.15 and VASL 6.2.2

### Download the script
1. Launch your browser (i.e. Safari, Firefox, Chrome)
2. Right-click on the link [vasl_setup.sh](https://raw.githubusercontent.com/vasl-devleopers/vasl/develop/install/vasl_setup.sh), and chose "Save as..." (or "Download Linked File As...") the file to your Downloads folder
3. Close your browser

### Update the script
**If you want to use the default ASL directory location and VASL version, skip this step and move on to "Run the script"**

**Currently, the Enhanced Scenarios have been saved with VASL 6.2.2, so if you use a later version of VASL,
you'll get a warning message about needing to convert the scenarios to the newer version**

1. Run TextEdit (Launchpad > Other > TextEdit)
2. Choose the "File > Open..." menu option
3. Navigate to your Downloads folder
4. Click on the "vasl_setup.sh" file
5. Click the "Open" button
6. If you like, update the value for ASLROOT to wherever you want to setup the ASL files
7. If you like, update the value for VASLVERSION to the 3 digit version (important, don't use periods in the version number) of VASL you want to download (e.g. "630")
8. Choose the "File > Save" menu option
9. Close TextEdit

### Run the script

The script defaults are:

* ASLROOT=${HOME}/ASL     
* VASLVERSION=622

**WARNING: This script deletes the following directories in the ASL structure**

* ${ASLROOT}/Scenarios/scenarios-master
* ${ASLROOT}/VASL/vasl-boards-extensions-master

**WARNING: This script recreates the following directories in the ASL structure**

* ${ASLROOT}/Scenarios/MMP/StarterKit/Enhanced
* ${ASLROOT}/VASL/boards
* ${ASLROOT}/VASL/extensions
* ${ASLROOT}/VASL/extensions-6.0
* ${ASLROOT}/VASL/extensions-common
* ${ASLROOT}/VASL/extensions-complete
* ${ASLROOT}/VASL/extensions-empty

**WARNING: If you care about any of these directories, back them up before running the script**

1. Run Terminal (Launchpad > Other > Terminal)
2. In the Terminal window, enter "cd Downloads" (or change Downloads to the path where you saved the file)
3. In the Terminal window, enter "bash vasl_setup.sh"
4. The script will finish in about a minute, depending on your Internet connection speed
5. Close Terminal

### Setup VASL module and preferences
1. Launch VASSAL (Launchpad > VASSAL)
2. Choose the "File > Open Module..." menu option
3. Navigate to your home directory, then to "ASL/VASL"
4. Click the "vasl-X.Y.Z.vmod" where X.Y.Z is the version (e.g. "vasl-6.2.2.vmod")
5. Click the "Open" button
6. A "Processing Image Tiles" window will pop up and show some processing.  It will close that window and launch VASL "Welcome" dialog when it's done
  * NOTE: The Welcome dialog may be behind other applications, like your web browser, so you may need to minimize applications until you see it. 
7. Click the "Cancel" button
8. In the "VASL controls" window, chose the "File > Preferences..." menu option
9. In the "Preferences" window, in the "General" tab, click the "Select" button next to "Board Directory"
10. Navigate to your home directory, then to "ASL/VASL/boards"
11. Click the "Open" button
12. In the "Preferences" window, in the "Extensions" tab, click the "Select" button next to "Extensions Directory"
13. Navigate to your home directory, then to "ASL/VASL/extensions-common"
14. Click the "Open" button
15. In the "Preferences" window, Click the "OK" button

### Test VASL setup by loading an Enhanced Scenario
1. In the "VASL controls" window, chose the "File > Load Game..." menu option
2. Navigate to home directory, then to "ASL/Scenarios/MMP/StarterKit/Enhanced"
3. Click on the "S 001 Retaking Vierville - Enhanced.vsav" scenario file
4. Click the "Open" button
5. The scenario should load just fine with no error messages in the "VASL controls" window

## On Linux
Note: Tested on Fedora 23
These instructions were verified on VASSAL 3.2.15 and VASL 6.2.2

### Download the script
1. Launch your browser (i.e. Safari, Firefox, Chrome)
2. Right-click on the link [vasl_setup.sh](https://raw.githubusercontent.com/vasl-devleopers/vasl/develop/install/vasl_setup.sh), and chose "Save as..." (or "Download Linked File As...") the file to your Downloads folder
3. Close your browser

### Update the script
**If you want to use the default ASL directory location and VASL version, skip this step and move on to "Run the script"**

**Currently, the Enhanced Scenarios have been saved with VASL 6.2.2, so if you use a later version of VASL,
you'll get a warning message about needing to convert the scenarios to the newer version**

1. Run gedit (Activities > Show Applications > gedit)
2. Click the "Open..." button
3. Navigate to your Downloads folder
4. Click on the "vasl_setup.sh" file
5. Click the "Open" button
6. If you like, update the value for ASLROOT to wherever you want to setup the ASL files
7. If you like, update the value for VASLVERSION to the 3 digit version (important, don't use periods in the version number) of VASL you want to download (e.g. "630")
8. Click "Save" button
9. Close gedit

### Run the script

The script defaults are:

* ASLROOT=${HOME}/ASL     
* VASLVERSION=622

**WARNING: This script deletes the following directories in the ASL structure**

* ${ASLROOT}/Scenarios/scenarios-master
* ${ASLROOT}/VASL/vasl-boards-extensions-master

**WARNING: This script recreates the following directories in the ASL structure**

* ${ASLROOT}/Scenarios/MMP/StarterKit/Enhanced
* ${ASLROOT}/VASL/boards
* ${ASLROOT}/VASL/extensions
* ${ASLROOT}/VASL/extensions-6.0
* ${ASLROOT}/VASL/extensions-common
* ${ASLROOT}/VASL/extensions-complete
* ${ASLROOT}/VASL/extensions-empty

**WARNING: If you care about any of these directories, back them up before running the script**

1. Run Terminal (Activities > Show Applications > Utilities > Terminal)
2. In the Terminal window, enter "cd Downloads" (or change Downloads to the path where you saved the file)
3. In the Terminal window, enter "bash vasl_setup.sh"
4. The script will finish in about a minute, depending on your Internet connection speed
5. Close Terminal

### Setup VASL module and preferences
1. Launch VASSAL (Activities > Show Applications > VASSAL)
2. Choose the "File > Open Module..." menu option
3. Navigate to your home directory, then to "ASL/VASL"
4. Click the "vasl-X.Y.Z.vmod" where X.Y.Z is the version (e.g. "vasl-6.2.2.vmod")
5. Click the "OK" button
6. A "Processing Image Tiles" window will pop up and show some processing.  It will close that window and launch VASL "Welcome" dialog when it's done
  * NOTE: The Welcome dialog may be behind other applications, like your web browser, so you may need to minimize applications until you see it. 
7. Click the "Cancel" button
8. In the "VASL controls" window, chose the "File > Preferences..." menu option
9. In the "Preferences" window, in the "General" tab, click the "Select" button next to "Board Directory"
10. Navigate to your home directory, then to "ASL/VASL/boards"
11. Click the "OK" button
12. In the "Preferences" window, in the "Extensions" tab, click the "Select" button next to "Extensions Directory"
13. Navigate to your home directory, then to "ASL/VASL/extensions-common" (you may have to scroll down to get to it)
14. Click the "OK" button
15. In the "Preferences" window, Click the "OK" button

### Test VASL setup by loading an Enhanced Scenario
1. In the "VASL controls" window, chose the "File > Load Game..." menu option
2. Navigate to home directory, then to "ASL/Scenarios/MMP/StarterKit/Enhanced"
3. Click on the "S 001 Retaking Vierville - Enhanced.vsav" scenario file
4. Click the "OK" button
5. The scenario should load just fine with no error messages in the "VASL controls" window
