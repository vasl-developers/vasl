@echo off
rem ==========================================================================
rem Author: Steve Smythe
rem Email: steve@smythefamily.com
rem GitHub: https://github.com/ssmythe/vasl-setup
rem --------------------------------------------------------------------------
rem This batch file (with embedded PowerShell) will:
rem - Create Pat's standard ASL directory structure
rem - Download the VASL vmod, Pat's enhanced scenarios, boards, and extensions
rem ...all in about a minute!
rem ==========================================================================


set ASLROOT=C:\ASL
set VASLVERSION=622

rem If you want to see the download status (warning: the download will be slowwwww), use DOWNLOADMODE=Continue
set DOWNLOADMODE=silentlyContinue
set SCRIPTNAME=%~dpn0


echo ----------------
echo VASL Directories
echo ----------------

echo Creating Pat Ireland ASL VASL and Players Directory Structure
mkdir "%ASLROOT%" 2> nul
mkdir "%ASLROOT%\VASL" 2> nul
mkdir "%ASLROOT%\VASL\Boards" 2> nul
mkdir "%ASLROOT%\VASL\Extensions" 2> nul
mkdir "%ASLROOT%\Players" 2> nul
mkdir "%ASLROOT%\Players\PatIreland" 2> nul

echo Creating Pat Ireland ASL Scenario Directory Structure
mkdir "%ASLROOT%\Scenarios\MMP\StarterKit" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\StarterKit\SK1" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\StarterKit\SK2" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\StarterKit\SK3" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\StarterKit\EP1" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\StarterKit\BP1" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Journal\Journal 1" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Journal\Journal 2" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Journal\Journal 3" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Journal\Journal 4" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Journal\Journal 5" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Journal\Journal 6" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Journal\Journal 7" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Journal\Journal 8" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\BeyondValor" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Yanks" 2> nul
mkdir "%ASLROOT%\Scenarios\MMP\Valor of the Guards" 2> nul
mkdir "%ASLROOT%\Scenarios\LeFrancTireur\FromTheCellar" 2> nul
mkdir "%ASLROOT%\Scenarios\LeFrancTireur\FromTheCellar\FTC 1" 2> nul
mkdir "%ASLROOT%\Scenarios\LeFrancTireur\FromTheCellar\FTC 2" 2> nul
mkdir "%ASLROOT%\Scenarios\LeFrancTireur\FromTheCellar\FTC 3" 2> nul
mkdir "%ASLROOT%\Scenarios\LeFrancTireur\FromTheCellar\FTC 4" 2> nul
mkdir "%ASLROOT%\Scenarios\LeFrancTireur\FromTheCellar\FTC 5" 2> nul
mkdir "%ASLROOT%\Scenarios\BoundingFire\BloodAndJungle" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit\Leatherneck" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit\Leatherneck\LN1" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit\Leatherneck\LN2" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit\Leatherneck\LN3" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit\HeroPack" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit\HeroPack\HP1" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit\HeroPack\HP2" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit\HeroPack\HP3" 2> nul
mkdir "%ASLROOT%\Scenarios\CriticalHit\HeroPack\HP4" 2> nul
echo.
echo.


echo ---------
echo VASL v%VASLVERSION%
echo ---------

echo Creating PowerShell script to download VASL
if exist "%SCRIPTNAME%.ps1" del /F "%SCRIPTNAME%.ps1"
echo $progressPreference = '%DOWNLOADMODE%' > "%SCRIPTNAME%.ps1"
echo $source = "http://vasl.info/modules/VASLv%VASLVERSION%.zip" >> "%SCRIPTNAME%.ps1"
echo $destination = "%ASLROOT%\VASL\VASLv%VASLVERSION%.zip" >> "%SCRIPTNAME%.ps1"
echo Invoke-WebRequest $source -OutFile $destination >> "%SCRIPTNAME%.ps1"
echo Add-Type -AssemblyName System.IO.Compression.FileSystem >> "%SCRIPTNAME%.ps1"
echo function Unzip >> "%SCRIPTNAME%.ps1"
echo { >> "%SCRIPTNAME%.ps1"
echo     param([string]$zipfile, [string]$outpath) >> "%SCRIPTNAME%.ps1"
echo >> "%SCRIPTNAME%.ps1"
echo     [System.IO.Compression.ZipFile]::ExtractToDirectory($zipfile, $outpath) >> "%SCRIPTNAME%.ps1"
echo } >> "%SCRIPTNAME%.ps1"
echo Unzip "%ASLROOT%\VASL\VASLv%VASLVERSION%.zip" "%ASLROOT%\VASL" >> "%SCRIPTNAME%.ps1"

if exist %ASLROOT%\VASL\VASLv%VASLVERSION%.zip del /F %ASLROOT%\VASL\VASLv%VASLVERSION%.zip 2>&1 1> nul
echo Downloading VASL zip from vasl.info and expanding it
PowerShell.exe -NoProfile -ExecutionPolicy Bypass -Command "& '%SCRIPTNAME%.ps1'" 2>&1 1> nul
echo Cleaning up downloaded zip
if exist %ASLROOT%\VASL\VASLv%VASLVERSION%.zip del /F /Q %ASLROOT%\VASL\VASLv%VASLVERSION%.zip 2>&1 1> nul
echo Cleaning up PowerShell script to download VASL
if exist "%SCRIPTNAME%.ps1" del /F "%SCRIPTNAME%.ps1"
echo VASL v%VASLVERSION% available!
echo.
echo.


echo ------------------
echo Enhanced Scenarios
echo ------------------

echo Creating PowerShell script to download scenarios
if exist "%SCRIPTNAME%.ps1" del /F "%SCRIPTNAME%.ps1"
echo $progressPreference = '%DOWNLOADMODE%' > "%SCRIPTNAME%.ps1"
echo $source = "https://github.com/vasl-developers/scenarios/archive/master.zip" >> "%SCRIPTNAME%.ps1"
echo $destination = "%ASLROOT%\Scenarios\master.zip" >> "%SCRIPTNAME%.ps1"
echo Invoke-WebRequest $source -OutFile $destination >> "%SCRIPTNAME%.ps1"
echo Add-Type -AssemblyName System.IO.Compression.FileSystem >> "%SCRIPTNAME%.ps1"
echo function Unzip >> "%SCRIPTNAME%.ps1"
echo { >> "%SCRIPTNAME%.ps1"
echo     param([string]$zipfile, [string]$outpath) >> "%SCRIPTNAME%.ps1"
echo >> "%SCRIPTNAME%.ps1"
echo     [System.IO.Compression.ZipFile]::ExtractToDirectory($zipfile, $outpath) >> "%SCRIPTNAME%.ps1"
echo } >> "%SCRIPTNAME%.ps1"
echo Unzip "%ASLROOT%\Scenarios\master.zip" "%ASLROOT%\Scenarios" >> "%SCRIPTNAME%.ps1"

if not exist %ASLROOT%\Scenarios\MMP\StarterKit mkdir %ASLROOT%\Scenarios\MMP\StarterKit
if exist %ASLROOT%\Scenarios\master.zip del /F %ASLROOT%\Scenarios\master.zip 2>&1 1> nul
if exist %ASLROOT%\Scenarios\scenarios-master rmdir /S /Q %ASLROOT%\Scenarios\scenarios-master 2>&1 1> nul
if exist %ASLROOT%\Scenarios\MMP\StarterKit\Enhanced rmdir /S /Q %ASLROOT%\Scenarios\MMP\StarterKit\Enhanced 2>&1 1> nul
echo Downloading Enhanced scenario zip from GitHit and expanding it
PowerShell.exe -NoProfile -ExecutionPolicy Bypass -Command "& '%SCRIPTNAME%.ps1'" 2>&1 1> nul
echo Relocating Enhanced directory to %ASLROOT%\Scenarios\MMP\StarterKit\Enhanced
move /Y %ASLROOT%\Scenarios\scenarios-master\MMP\StarterKit\Enhanced %ASLROOT%\Scenarios\MMP\StarterKit > nul
echo Cleaning up downloaded zip and expanded directory
if exist %ASLROOT%\Scenarios\master.zip del /F /Q %ASLROOT%\Scenarios\master.zip 2>&1 1> nul
if exist %ASLROOT%\Scenarios\scenarios-master rmdir /S /Q %ASLROOT%\Scenarios\scenarios-master > nul
echo Cleaning up PowerShell script to download scenarios
if exist "%SCRIPTNAME%.ps1" del /F "%SCRIPTNAME%.ps1"
echo Enhanced Scenarios available!
echo.
echo.


echo --------------------------
echo VASL Boards and Extensions
echo --------------------------
echo Creating PowerShell script to download VASL boards
if exist "%SCRIPTNAME%.ps1" del /F "%SCRIPTNAME%.ps1"
echo $progressPreference = '%DOWNLOADMODE%' > "%SCRIPTNAME%.ps1"
echo $source = "https://github.com/vasl-developers/vasl-boards-extensions/archive/master.zip" >> "%SCRIPTNAME%.ps1"
echo $destination = "%ASLROOT%\VASL\master.zip" >> "%SCRIPTNAME%.ps1"
echo Invoke-WebRequest $source -OutFile $destination >> "%SCRIPTNAME%.ps1"
echo Add-Type -AssemblyName System.IO.Compression.FileSystem >> "%SCRIPTNAME%.ps1"
echo function Unzip >> "%SCRIPTNAME%.ps1"
echo { >> "%SCRIPTNAME%.ps1"
echo     param([string]$zipfile, [string]$outpath) >> "%SCRIPTNAME%.ps1"
echo >> "%SCRIPTNAME%.ps1"
echo     [System.IO.Compression.ZipFile]::ExtractToDirectory($zipfile, $outpath) >> "%SCRIPTNAME%.ps1"
echo } >> "%SCRIPTNAME%.ps1"
echo Unzip "%ASLROOT%\VASL\master.zip" "%ASLROOT%\VASL" >> "%SCRIPTNAME%.ps1"

if exist %ASLROOT%\VASL\master.zip del /F %ASLROOT%\VASL\master.zip 2>&1 1> nul
if exist %ASLROOT%\VASL\vasl-boards-extensions-master rmdir /S /Q %ASLROOT%\VASL\vasl-boards-extensions-master 2>&1 1> nul
if exist %ASLROOT%\VASL\boards rmdir /S /Q %ASLROOT%\VASL\boards 2>&1 1> nul
if exist %ASLROOT%\VASL\extensions rmdir /S /Q %ASLROOT%\VASL\extensions 2>&1 1> nul
if exist %ASLROOT%\VASL\extensions-6.0 rmdir /S /Q %ASLROOT%\VASL\extensions-6.0 2>&1 1> nul
if exist %ASLROOT%\VASL\extensions-complete rmdir /S /Q %ASLROOT%\VASL\extensions-complete 2>&1 1> nul
if exist %ASLROOT%\VASL\extensions-empty rmdir /S /Q %ASLROOT%\VASL\extensions-empty 2>&1 1> nul
if not exist %ASLROOT%\VASL\extensions-empty mkdir %ASLROOT%\VASL\extensions-empty 2>&1 1> nul
echo Downloading VASL boards and extensions zip from GitHit and expanding it
echo Warning: this is a 273 MB download.  It's big!
echo The download takes about a minute on a 60 Mbps connection...
PowerShell.exe -NoProfile -ExecutionPolicy Bypass -Command "& '%SCRIPTNAME%.ps1'" 2>&1 1> nul
echo Relocating Enhanced directory to %ASLROOT%\Scenarios\MMP\StarterKit\Enhanced
move /Y %ASLROOT%\VASL\vasl-boards-extensions-master\boards %ASLROOT%\VASL\boards > nul
move /Y %ASLROOT%\VASL\vasl-boards-extensions-master\extensions %ASLROOT%\VASL\extensions > nul
move /Y %ASLROOT%\VASL\vasl-boards-extensions-master\extensions-6.0 %ASLROOT%\VASL\extensions-6.0 > nul
move /Y %ASLROOT%\VASL\vasl-boards-extensions-master\extensions-complete %ASLROOT%\VASL\extensions-complete > nul
echo Cleaning up downloaded zip and expanded directory
if exist %ASLROOT%\VASL\master.zip del /F /Q %ASLROOT%\VASL\master.zip 2>&1 1> nul
if exist %ASLROOT%\VASL\vasl-boards-extensions-master rmdir /S /Q %ASLROOT%\VASL\vasl-boards-extensions-master > nul
echo Cleaning up PowerShell script to download scenarios
if exist "%SCRIPTNAME%.ps1" del /F "%SCRIPTNAME%.ps1"
echo VASL boards and extentions available!
echo.
echo.


echo ----------------------
echo VASL extensions-common
echo ----------------------
if exist %ASLROOT%\VASL\extensions-common rmdir /S /Q %ASLROOT%\VASL\extensions-common 2>&1 1> nul
if not exist %ASLROOT%\VASL\extensions-common mkdir %ASLROOT%\VASL\extensions-common 2>&1 1> nul

rem extensions-6.0
copy /y %ASLROOT%\VASL\extensions-6.0\*.vmdx %ASLROOT%\VASL\extensions-common 2>&1 1> nul

rem remove obsolete extensions
if exist %ASLROOT%\VASL\extensions-common\chatter-plus-2.21.vmdx del /F %ASLROOT%\VASL\extensions-common\chatter-plus-2.21.vmdx 2>&1 1> nul

rem extensions-complete
copy /y %ASLROOT%\VASL\extensions-complete\3d6.mdx %ASLROOT%\VASL\extensions-common 2>&1 1> nul
copy /y %ASLROOT%\VASL\extensions-complete\5VBM.mdx %ASLROOT%\VASL\extensions-common 2>&1 1> nul
copy /y %ASLROOT%\VASL\extensions-complete\P-dice.mdx %ASLROOT%\VASL\extensions-common 2>&1 1> nul
copy /y %ASLROOT%\VASL\extensions-complete\Rare_Vehicles?Ordnance.mdx %ASLROOT%\VASL\extensions-common 2>&1 1> nul

dir /b %ASLROOT%\VASL\extensions-common
:EOF
