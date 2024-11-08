#!/bin/bash

# Get the directory of the script to ensure relative paths work
SCRIPT_DIR=$(dirname "$0")

# Define paths relative to the script's location
VERSION_FILE="$SCRIPT_DIR/boards/v5boardVersions.xml"
BD_FILES_DIR="$SCRIPT_DIR/boards/bdFiles"
TEMP_DIR=$(mktemp -d)
mismatches=()  # Array to hold any mismatch messages

# Define cleanup function to remove temporary directory
cleanup() {
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT  # Ensure cleanup runs on exit

# Function to decode HTML entities in names
decode_html_entities() {
    local name="$1"
    name="${name//&#x20;/ }"   # Replace &#x20; with a space
    name="${name//&amp;/&}"    # Replace &amp; with &
    # Add more replacements as needed
    echo "$name"
}

# Extract version information from a file
extract_version() {
    local file_path="$1"
    local filename=$(basename "$file_path")
    local version_line
    local zip_version

    # If the file is named 'data', extract version in 'version X.Y' format
    if [[ "$filename" == "data" ]]; then
        version_line=$(grep '^version ' "$file_path")
        zip_version=$(echo "$version_line" | awk '{print $2}' | tr -d '\r' | xargs)
    else
        # Skip the first line to avoid capturing the XML declaration
        version_line=$(tail -n +2 "$file_path" | grep 'version="' | head -n 1)
        # Extract and clean the version attribute from the matched line
        zip_version=$(echo "$version_line" | grep -o 'version="[^"]*"' | sed -e 's/version="//' -e 's/"//' | tr -d '\r' | xargs)
    fi

    echo "$zip_version"
}

# Extract names and versions from v5boardVersions.xml with HTML decoding
declare -A board_versions
while read -r line; do
    if [[ $line =~ name=\"([^\"]+)\" ]]; then
        board_name=$(decode_html_entities "${BASH_REMATCH[1]}")
    fi
    if [[ $line =~ version=\"([^\"]+)\" ]]; then
        board_version="${BASH_REMATCH[1]}"
        board_versions["$board_name"]=$(echo "$board_version" | tr -d '\r' | xargs)
    fi
done < <(grep '<boarddata' "$VERSION_FILE")

# Arrays to store warnings and mismatches separately
warnings=()
mismatches=()

# Check each file in boards/bdFiles
for board_file in "$BD_FILES_DIR"/*; do
    filename=$(basename "$board_file")

    # Skip files that do not start with 'bd'
    if [[ ! $filename == bd* ]]; then
        continue
    fi

    board_name="${filename#bd}"  # Strip "bd" prefix

    # Decode any HTML entities in the board name for accurate matching
    board_name_clean=$(decode_html_entities "$board_name" | tr -d '\r' | xargs)

    # Check if the modified filename matches any board name in v5boardVersions.xml
    if [[ -v board_versions["$board_name_clean"] ]]; then
        expected_version="${board_versions["$board_name_clean"]}"

        file_type=$(file -b "$board_file")
        metadata_file=""

        if [[ "$file_type" == *"Zip archive data"* ]]; then
            # Extract from ZIP archive
            if command -v unzip &> /dev/null; then
                unzip -q -j "$board_file" -d "$TEMP_DIR" "*[Bb]oard[Mm]etadata.xml*" "data" 2>/dev/null

                if [[ -f "$TEMP_DIR/BoardMetadata.xml" ]]; then
                    metadata_file="$TEMP_DIR/BoardMetadata.xml"
                elif [[ -f "$TEMP_DIR/boardMetadata.xml" ]]; then
                    metadata_file="$TEMP_DIR/boardMetadata.xml"
                elif [[ -f "$TEMP_DIR/data" ]]; then
                    metadata_file="$TEMP_DIR/data"
                else
                    warnings+=("Warning: Neither BoardMetadata.xml nor data file found in $board_file.")
                    continue
                fi
            else
                echo "Error: unzip command is not available."
                exit 1
            fi
        elif [[ "$file_type" == *"ASCII text"* || "$file_type" == *"UTF-8 Unicode text"* ]]; then
            # Treat as plain text file
            metadata_file="$board_file"
        else
            warnings+=("Warning: Unsupported file type for $board_file. Skipping.")
            continue
        fi

        zip_version=$(extract_version "$metadata_file")
        expected_version=$(echo "$expected_version" | tr -d '\r' | xargs)  # Trim whitespace and carriage returns

        # Check for version mismatch after trimming
        if [[ "$zip_version" != "$expected_version" ]]; then
            mismatches+=("Version mismatch for board '$board_name_clean' in $board_file: expected $expected_version, found $zip_version")
        fi

        # Clean up for the next iteration
        rm -f "$TEMP_DIR"/*
    else
        warnings+=("Warning: $board_file does not correspond to any board in v5boardVersions.xml.")
    fi
done

# Print mismatches and warnings separately
if [[ ${#mismatches[@]} -gt 0 ]]; then
    echo "Version Mismatches Found:"
    printf "%s\n" "${mismatches[@]}"
fi

if [[ ${#warnings[@]} -gt 0 ]]; then
    echo "Warnings:"
    printf "%s\n" "${warnings[@]}"
fi

# Exit with an error if there are mismatches
if [[ ${#mismatches[@]} -gt 0 ]]; then
    exit 1
else
    echo "All checks passed."
fi
