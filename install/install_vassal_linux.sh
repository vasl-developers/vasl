#!/usr/bin/env bash

# ==========================================================================
# Author: Steve Smythe
# Email: steve@smythefamily.com
# GitHub: https://github.com/ssmythe/vasl-setup
# --------------------------------------------------------------------------
# This shell script will:
# - Download and install VASSAL
# - Create a Gnome3 application icon
# ...all in about a minute!
# ==========================================================================

VASSAL_VERSION=3.2.15
VASSAL_ROOT="${HOME}/VASSAL-${VASSAL_VERSION}"

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

function unbz2_file()
{
    filename="$1"
    outpath="$2"
    pushd . 2>&1 1>/dev/null
    cd "${outpath}"
    tar xjvf "${filename}" 2>&1 1>/dev/null
    popd 2>&1 1>/dev/null
}

function download_and_unzip()
{
    url="$1"
    filename="$2"
    outpath="$3"
    cleanup_file "${filename}"
    download_file "${url}" "${filename}"
    unbz2_file "${filename}" "${outpath}"
    cleanup_file "${filename}"
}


SCRIPTNAME="$(dirname $0)/$(basename $0)"


echo -------------
echo VASSAL ENGINE
echo -------------
echo Downloading VASSAL engine ${VASSAL_VERSION}
echo Warning: this is a 18 MB download.
echo The download takes about a minute on a 60 Mbps connection...

cleanup_directory "${VASSAL_ROOT}"
ensure_directory "${HOME}/Downloads"
cleanup_directory "${HOME}/Downloads/VASSAL-${VASSAL_VERSION}"
download_and_unzip "http://downloads.sourceforge.net/vassalengine/VASSAL-${VASSAL_VERSION}-linux.tar.bz2" "${HOME}/Downloads/VASSAL-${VASSAL_VERSION}-linux.tar.bz2" "${HOME}/Downloads"
mv "${HOME}/Downloads/VASSAL-${VASSAL_VERSION}" "${VASSAL_ROOT}"

echo Downloading the VASSAL application shortcut icon
download_file "https://raw.githubusercontent.com/vasl-developer/vasl/master/install/VASSAL.png" "${VASSAL_ROOT}/lib/VASSAL.png"

echo Creating VASSAL application shortcut
cat - >"${HOME}/.local/share/applications/vassal.desktop" <<EOF
[Desktop Entry]
Type=Application
Encoding=UTF-8
Name=VASSAL
Comment=The open-source boardgame engine
Icon=${VASSAL_ROOT}/lib/VASSAL.png
Exec=${VASSAL_ROOT}/VASSAL.sh
Terminal=false
Categories=Game
EOF
