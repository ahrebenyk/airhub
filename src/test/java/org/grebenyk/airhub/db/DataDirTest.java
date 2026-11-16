package org.grebenyk.airhub.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Location of the database file (the installed copy cannot write next to the program in Program Files). */
class DataDirTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(Db.DATA_DIR_PROPERTY);
    }

    @Test
    void defaultDirectoryIsDataInWorkingDirectory() {
        assertEquals(Path.of("data").toAbsolutePath(), Db.dataDir());
        assertEquals(Path.of("data", "airhub").toAbsolutePath(), Db.file());
    }

    @Test
    void propertyOverridesDirectory() {
        System.setProperty(Db.DATA_DIR_PROPERTY, "build/test-db");
        assertEquals(Path.of("build/test-db").toAbsolutePath(), Db.dataDir());
    }

    @Test
    void blankPropertyIsIgnored() {
        System.setProperty(Db.DATA_DIR_PROPERTY, " ");
        assertEquals(Path.of("data").toAbsolutePath(), Db.dataDir());
    }
}
