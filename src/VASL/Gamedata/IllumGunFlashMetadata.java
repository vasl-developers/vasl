package VASL.Gamedata;

public class IllumGunFlashMetadata {



        /*
            name = game piece name (i.e. piece.getName())
            hindrance = # of hindrances/hex
            height = height of LOS hindrance
            terrain = map terrain type
            level - denotes location level
            position - for location denotes if pieces above/below the counter are in the location.
            coverArch - covered arch of location
            rotation - used only for Barrage, rotation of Barrage counter
            isBarrange - used for OBA/Barrage

         */
        private String name;
        private String terrain;
        private int range;
        private int hindrance;
        private VASL.Gamedata.IllumGunFlashMetadata.CounterType type;
        private int level;
        private String position;
        private int coverArch;
        private int rotation;
        private boolean isBarrage;
        private int hside;

        public static enum CounterType {STARSHELL, ILLUMROUND, FLAME, BLAZE, TRIPFLARES, SEARCHLIGHTS}

        public IllumGunFlashMetadata(String name, VASL.Gamedata.IllumGunFlashMetadata.CounterType type) {
            this.name = name;
            this.type = type;
        }

        /**
         * @return the counter name
         */
        public String getName() {
            return name;
        }

        /**
         * @return the terrain name (for terrain-type counters)
         */
        public String getTerrain() {
            return terrain;
        }

        /**
         * @return the height (for smoke-type counters)
         */
        public int getRange() {
            return range;
        }

        /**
         * @return the hindrance amount (for smoke counters)
         */
        public int getHindrance() {
            return hindrance;
        }

        /**
         * @return the hexside amount (for Rowhouse counters)
         */
        public int getHexside() {
            return hside;
        }
        /**
         * @return the counter type
         */
        public IllumGunFlashMetadata.CounterType getType() {
            return type;
        }

        /**
         * Set the terrain (for terrain-type counters)
         * @param terrain the terrain name
         */
        public void setTerrain(String terrain) {
            this.terrain = terrain;
        }

        /**
         * Set the range (for Area for Effect type counter)
         * @param range the range of AfE
         */
        public void setRange(int range) {
            this.range = this.range;
        }

        /**
         * Set the smoke hindrance (for smoke-type counters)
         * @param hindrance the smoke hindrance level
         */
        public void setHindrance(int hindrance) {
            this.hindrance = hindrance;
        }

        /**
         * Set the hexside
         * @param hside the hexside
         */
        public void setHexside(int hexside) {
            this.hside = hexside;
        }
        /**
         * @return the location level
         */
        public int getLevel() {
            return level;
        }

        /**
         * Set the location level
         * @param level the level
         */
        public void setLevel(int level) {
            this.level = level;
        }

        /**
         * @return the piece covered arch
         */
        public int getCoverArch() {
            return coverArch -1;
        }  //counters use 1-6, code uses 0-5

        /**
         * Set the piece covered arch
         * @param coverArch the covered arch
         */
        public void setCoverArch(int coverArch) {
            this.coverArch = coverArch;
        }

        /**
         * @return the location position
         */
        public String getPosition() {
            return position;
        }

        /**
         * Set the location position
         * @param position the covered arch
         */
        public void setPosition(String position) {
            this.position = position;
        }

        /**
         * @return the rotation for Barrage/OBA
         */
        public int getRotation() {
            return rotation;
        }

        /**
         * Set the rotation for Barrage/OBA
         */
        public void setRotation(int rotation) {
            this.rotation = rotation;
        }

        /**
         * @return is Barrage
         */
        public boolean getIsBarrage() {
            return isBarrage;
        }

        /**
         * Set is Barrage
         */
        public void setIsBarrage(boolean isBarrage) {
            this.isBarrage = isBarrage;
        }

    }
