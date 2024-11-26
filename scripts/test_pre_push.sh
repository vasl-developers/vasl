#!/bin/bash


# Define paths relative to the repository root
REPO_ROOT=$(git rev-parse --show-toplevel)
VERSION_FILE="$REPO_ROOT/boards/v5boardVersions.xml"
BD_FILES_DIR="$REPO_ROOT/boards/bdFiles"
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
extract_board_name() {
    local file_path="$1"
    local board_name

     # Skip the first line to avoid capturing the XML declaration
            name_line=$(tail -n +2 "$file_path" | grep 'name="' | head -n 1)
            # Extract and clean the version attribute from the matched line
            board_name=$(echo "$name_line" | grep -o 'name="[^"]*"' | sed -e 's/name="//' -e 's/"//' | tr -d '\r' | xargs)

    echo "$board_name"
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

            if command -v unzip &> /dev/null; then
                # List the ZIP file's contents
                zip_contents=$(unzip -l "$board_file" | awk '{print $4}' | tail -n +4 | head -n -2)

                # Check if either BoardMetadata.xml or data exists in the root directory
                has_board_metadata=$(echo "$zip_contents" | grep -q "^BoardMetadata.xml$"; echo $?)
                has_data=$(echo "$zip_contents" | grep -q "^data$"; echo $?)

                # If neither file is found, print a warning message
                if [[ $has_board_metadata -ne 0 && $has_data -ne 0 ]]; then
                    warnings+=("Warning: Neither BoardMetadata.xml nor data file is present in the root of $board_file.")
                    continue
                fi

                # Extract the file that is present for further validation
                if [[ $has_board_metadata -eq 0 ]]; then
                    unzip -q -j "$board_file" "BoardMetadata.xml" -d "$TEMP_DIR" 2>/dev/null
                    metadata_file="$TEMP_DIR/BoardMetadata.xml"
                    zip_board_name=$(extract_board_name "$metadata_file")
                    # If the name in the metadatafile does not match, there will
                    # be an error when loading game so we make sure they match
                    if [[ "$zip_board_name" != "$board_name_clean" ]]; then
                        mismatches+=("Board name mismatch in $board_file: expected '$board_name_clean', found '$zip_board_name'")
                    fi
                elif [[ $has_data -eq 0 ]]; then
                    unzip -q -j "$board_file" "data" -d "$TEMP_DIR" 2>/dev/null
                    metadata_file="$TEMP_DIR/data"
                fi
            else
                echo "Error: unzip command is not available."
                exit 1
            fi

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

# Check for entries in v5boardVersions.xml without corresponding bdFiles
for board_name in "${!board_versions[@]}"; do
    board_file="$BD_FILES_DIR/bd$board_name"
    if [[ ! -f "$board_file" ]]; then
        warnings+=("Warning: No file found in bdFiles directory for board '$board_name' listed in v5boardVersions.xml.")
    fi
done


if [[ ${#warnings[@]} -gt 0 ]]; then
    echo "Warnings:"
    printf "%s\n" "${warnings[@]}"
fi

# Print mismatches and warnings separately
if [[ ${#mismatches[@]} -gt 0 ]]; then
    echo "Version Mismatches Found:"
    printf "%s\n" "${mismatches[@]}"
fi

# Exit with an error if there are mismatches
if [[ ${#mismatches[@]} -gt 0 ]]; then
    echo "Error: Version mismatches found. Please fix the issues before pushing."
    exit 1
else
    echo "All checks passed."
fi