package VASL.build.module;

import java.io.File;

public class FileHelper {

    /// Helper function to test if a file using the pre Java 25 specification
    public static boolean FileExists(File file)
    {
        if (file == null) return false;
        /// The problem is that File.exists() returns true for empty strings in Java 25, and you might
        /// (entirely reasonably) have been relying on that to return false like it always had.
        ///
        /// If you want a test that behaves like pre-Java 25 f.exists() for all versions,
        /// then you'd need to do !f.getPath().isEmpty() && f.exists().
        if (file.getPath().isEmpty()) return false;
        return file.exists();
    }
}
