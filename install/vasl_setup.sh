#!/usr/bin/env bash

# ==========================================================================
# Author: Steve Smythe
# Email: steve@smythefamily.com
# GitHub: https://github.com/ssmythe/vasl-setup
# --------------------------------------------------------------------------
# This shell script will:
# - Create Pat's standard ASL directory structure
# - Download the VASL vmod, Pat's enhanced scenarios, boards, and extensions
# ...all in about a minute!
# ==========================================================================

ASLROOT="${HOME}/ASL"
VASLVERSION=622


function cleanup_file()
{
    filename="$1"
    if [ -f "${filename}" ]; then
        rm -f "${filename}"
    fi
}

function cleanup_directory()
{
    directory="$1"
    if [ -d "${directory}" ]; then
        rm -fr "${directory}"
    fi
}

function ensure_directory()
{
    directory="$1"
    if [ ! -d "${directory}" ]; then
        mkdir -p "${directory}"
    fi
}

function download_file()
{
    url="$1"
    filename="$2"
    curl -ssL "${url}" -o "${filename}"
}

function unzip_file()
{
    filename="$1"
    outpath="$2"
    unzip -o "${filename}" -d "${outpath}" 2>&1 1>/dev/null
}

function download_and_unzip()
{
    url="$1"
    filename="$2"
    outpath="$3"
    cleanup_file "${filename}"
    download_file "${url}" "${filename}"
    unzip_file "${filename}" "${outpath}"
    cleanup_file "${filename}"
}


SCRIPTNAME="$(dirname $0)/$(basename $0)"


echo ----------------
echo VASL Directories
echo ----------------

echo Creating Pat Ireland ASL VASL and Players Directory Structure
ensure_directory "${ASLROOT}" 
ensure_directory "${ASLROOT}/VASL"
ensure_directory "${ASLROOT}/VASL/Boards"
ensure_directory "${ASLROOT}/VASL/Extensions"
ensure_directory "${ASLROOT}/Players"
ensure_directory "${ASLROOT}/Players/PatIreland"

echo Creating Pat Ireland ASL Scenario Directory Structure
ensure_directory "${ASLROOT}/Scenarios/MMP/StarterKit" 
ensure_directory "${ASLROOT}/Scenarios/MMP/StarterKit/SK1" 
ensure_directory "${ASLROOT}/Scenarios/MMP/StarterKit/SK2" 
ensure_directory "${ASLROOT}/Scenarios/MMP/StarterKit/SK3" 
ensure_directory "${ASLROOT}/Scenarios/MMP/StarterKit/EP1" 
ensure_directory "${ASLROOT}/Scenarios/MMP/StarterKit/BP1" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Journal/Journal 1" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Journal/Journal 2" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Journal/Journal 3" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Journal/Journal 4" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Journal/Journal 5" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Journal/Journal 6" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Journal/Journal 7" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Journal/Journal 8" 
ensure_directory "${ASLROOT}/Scenarios/MMP/BeyondValor" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Yanks" 
ensure_directory "${ASLROOT}/Scenarios/MMP/Valor of the Guards" 
ensure_directory "${ASLROOT}/Scenarios/LeFrancTireur/FromTheCellar" 
ensure_directory "${ASLROOT}/Scenarios/LeFrancTireur/FromTheCellar/FTC 1" 
ensure_directory "${ASLROOT}/Scenarios/LeFrancTireur/FromTheCellar/FTC 2" 
ensure_directory "${ASLROOT}/Scenarios/LeFrancTireur/FromTheCellar/FTC 3" 
ensure_directory "${ASLROOT}/Scenarios/LeFrancTireur/FromTheCellar/FTC 4" 
ensure_directory "${ASLROOT}/Scenarios/LeFrancTireur/FromTheCellar/FTC 5" 
ensure_directory "${ASLROOT}/Scenarios/BoundingFire/BloodAndJungle" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit/Leatherneck" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit/Leatherneck/LN1" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit/Leatherneck/LN2" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit/Leatherneck/LN3" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit/HeroPack" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit/HeroPack/HP1" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit/HeroPack/HP2" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit/HeroPack/HP3" 
ensure_directory "${ASLROOT}/Scenarios/CriticalHit/HeroPack/HP4" 
echo ""
echo ""


echo ---------
echo VASL v${VASLVERSION}
echo ---------

echo Downloading VASL zip from vasl.info and expanding it
download_and_unzip "http://vasl.info/modules/VASLv${VASLVERSION}.zip" "${ASLROOT}/VASL/VASLv${VASLVERSION}.zip" "${ASLROOT}/VASL"
echo VASL v${VASLVERSION} available!
echo ""
echo ""


echo ------------------
echo Enhanced Scenarios
echo ------------------

ensure_directory "${ASLROOT}/Scenarios/MMP/StarterKit"
cleanup_directory "${ASLROOT}/Scenarios/scenarios-master"
cleanup_directory "${ASLROOT}/Scenarios/MMP/StarterKit/Enhanced"
echo Downloading Enhanced scenario zip from GitHit and expanding it
download_and_unzip "https://github.com/vasl-developers/scenarios/archive/master.zip" "${ASLROOT}/Scenarios/master.zip" "${ASLROOT}/Scenarios"
echo Relocating Enhanced directory to ${ASLROOT}/Scenarios/MMP/StarterKit/Enhanced
mv "${ASLROOT}/Scenarios/scenarios-master/MMP/StarterKit/Enhanced" "${ASLROOT}/Scenarios/MMP/StarterKit"
echo Cleaning up downloaded zip and expanded directory
cleanup_directory "${ASLROOT}/Scenarios/scenarios-master"
echo Enhanced Scenarios available!
echo ""
echo ""


echo --------------------------
echo VASL Boards and Extensions
echo --------------------------
cleanup_directory "${ASLROOT}/VASL/vasl-boards-extensions-master"
cleanup_directory "${ASLROOT}/VASL/boards"
cleanup_directory "${ASLROOT}/VASL/extensions"
cleanup_directory "${ASLROOT}/VASL/extensions-6.0"
cleanup_directory "${ASLROOT}/VASL/extensions-complete"
cleanup_directory "${ASLROOT}/VASL/extensions-empty"
ensure_directory "${ASLROOT}/VASL/extensions-empty"
echo Downloading VASL boards and extensions zip from GitHit and expanding it
echo Warning: this is a 273 MB download.  It\'s big!
echo The download takes about a minute on a 60 Mbps connection...
download_and_unzip "https://github.com/vasl-developers/vasl-boards-extensions/archive/master.zip" "${ASLROOT}/VASL/master.zip" "${ASLROOT}/VASL"
echo Relocating Enhanced directory to ${ASLROOT}/Scenarios/MMP/StarterKit/Enhanced
mv "${ASLROOT}/VASL/vasl-boards-extensions-master/boards" "${ASLROOT}/VASL/boards"
mv "${ASLROOT}/VASL/vasl-boards-extensions-master/extensions" "${ASLROOT}/VASL/extensions"
mv "${ASLROOT}/VASL/vasl-boards-extensions-master/extensions-6.0" "${ASLROOT}/VASL/extensions-6.0"
mv "${ASLROOT}/VASL/vasl-boards-extensions-master/extensions-complete" "${ASLROOT}/VASL/extensions-complete"
echo Cleaning up downloaded zip and expanded directory
cleanup_directory "${ASLROOT}/VASL/vasl-boards-extensions-master"
echo VASL boards and extentions available!
echo ""
echo ""


echo ----------------------
echo VASL extensions-common
echo ----------------------
cleanup_directory "${ASLROOT}/VASL/extensions-common"
ensure_directory "${ASLROOT}/VASL/extensions-common"

# extensions-6.0
cp -f "${ASLROOT}"/VASL/extensions-6.0/*.vmdx "${ASLROOT}/VASL/extensions-common"

# remove obsolete extensions
cleanup_file "${ASLROOT}/VASL/extensions-common/chatter-plus-2.21.vmdx"

# extensions-complete
cp -f "${ASLROOT}/VASL/extensions-complete/3d6.mdx" "${ASLROOT}/VASL/extensions-common"
cp -f "${ASLROOT}/VASL/extensions-complete/5VBM.mdx" "${ASLROOT}/VASL/extensions-common"
cp -f "${ASLROOT}/VASL/extensions-complete/P-dice.mdx" "${ASLROOT}/VASL/extensions-common"
cp -f "${ASLROOT}/VASL/extensions-complete/Rare_Vehicles&Ordnance.mdx" "${ASLROOT}/VASL/extensions-common"

ls -1 "${ASLROOT}/VASL/extensions-common"
