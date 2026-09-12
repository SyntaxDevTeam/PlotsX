package pl.syntaxdevteam.plotsx.databases

/** Shared schema for startup and portable backups. */
internal object DatabaseSchema {
    fun statements(dbType: String): List<String> = buildList {
        val createPlotsTable = when (dbType) {
            "sqlite" -> """
                CREATE TABLE IF NOT EXISTS plots (
                    plot_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    radius INTEGER NOT NULL,
                    world TEXT NOT NULL,
                    name TEXT NOT NULL,
                    creation_time TEXT NOT NULL,
                    UNIQUE(x, z, world)
                );
            """.trimIndent()

            "postgresql" -> """
                CREATE TABLE IF NOT EXISTS plots (
                    plot_id SERIAL PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    x INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    radius INTEGER NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    creation_time BIGINT NOT NULL,
                    UNIQUE(x, z, world)
                );
            """.trimIndent()

            "h2" -> """
                CREATE TABLE IF NOT EXISTS plots (
                    plot_id INT AUTO_INCREMENT PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    x INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    radius INTEGER NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    creation_time BIGINT NOT NULL,
                    UNIQUE(x, z, world)
                );
            """.trimIndent()

            else -> """
                CREATE TABLE IF NOT EXISTS plots (
                    plot_id INT AUTO_INCREMENT PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    x INT NOT NULL,
                    z INT NOT NULL,
                    y INTEGER NOT NULL,
                    radius INT NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    creation_time BIGINT NOT NULL,
                    
                    INDEX idx_owner_uuid (owner_uuid),
                    INDEX idx_world (world),
                    INDEX idx_coordinates (x, z),
                    INDEX idx_radius (radius),
                    UNIQUE KEY unique_location (x, z, world)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """.trimIndent()
        }
        add(createPlotsTable)
        add("""
            CREATE TABLE IF NOT EXISTS plot_segments (
                plot_id INTEGER NOT NULL,
                x INTEGER NOT NULL,
                z INTEGER NOT NULL,
                radius INTEGER NOT NULL,
                PRIMARY KEY (plot_id, x, z),
                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
            )
        """.trimIndent())
        add("""
            CREATE TABLE IF NOT EXISTS plot_expansion_levels (
                plot_id INTEGER PRIMARY KEY,
                expansion_level INTEGER NOT NULL,
                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
            )
        """.trimIndent())

        val createPlotMembersTable = when (dbType) {
            "sqlite" -> """
                CREATE TABLE IF NOT EXISTS plot_members (
                    plot_id INTEGER NOT NULL,
                    member_uuid TEXT NOT NULL,
                    role TEXT NOT NULL,
                    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                    UNIQUE(plot_id, member_uuid)
                );
            """.trimIndent()

            "postgresql" -> """
                CREATE TABLE IF NOT EXISTS plot_members (
                    plot_id INTEGER NOT NULL,
                    member_uuid VARCHAR(36) NOT NULL,
                    role VARCHAR(255) NOT NULL,
                    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                    UNIQUE(plot_id, member_uuid)
                );
            """.trimIndent()

            "h2" -> """
                CREATE TABLE IF NOT EXISTS plot_members (
                    plot_id INT NOT NULL,
                    member_uuid VARCHAR(36) NOT NULL,
                    role VARCHAR(255) NOT NULL,
                    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                    UNIQUE(plot_id, member_uuid)
                );
            """.trimIndent()

            else -> """
                CREATE TABLE IF NOT EXISTS plot_members (
                    plot_id INT NOT NULL,
                    member_uuid VARCHAR(36) NOT NULL,
                    role VARCHAR(255) NOT NULL,
                    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                    UNIQUE(plot_id, member_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """.trimIndent()
        }
        add(createPlotMembersTable)

        val createPlotFlagsTable = when (dbType) {
            "sqlite" -> """
                CREATE TABLE IF NOT EXISTS plot_flags (
                    plot_id INTEGER NOT NULL,
                    flag_name TEXT NOT NULL,
                    flag_value TEXT NOT NULL,
                    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                    UNIQUE(plot_id, flag_name)
                );
            """.trimIndent()

            "postgresql" -> """
                CREATE TABLE IF NOT EXISTS plot_flags (
                    plot_id INTEGER NOT NULL,
                    flag_name VARCHAR(255) NOT NULL,
                    flag_value VARCHAR(255) NOT NULL,
                    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                    UNIQUE(plot_id, flag_name)
                );
            """.trimIndent()

            "h2" -> """
                CREATE TABLE IF NOT EXISTS plot_flags (
                    plot_id INT NOT NULL,
                    flag_name VARCHAR(255) NOT NULL,
                    flag_value VARCHAR(255) NOT NULL,
                    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                    UNIQUE(plot_id, flag_name)
                );
            """.trimIndent()

            else -> """
                CREATE TABLE IF NOT EXISTS plot_flags (
                    plot_id INT NOT NULL,
                    flag_name VARCHAR(255) NOT NULL,
                    flag_value VARCHAR(255) NOT NULL,
                    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                    UNIQUE(plot_id, flag_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """.trimIndent()
        }
        add(createPlotFlagsTable)

        val createPlotLogsTable = when (dbType) {
            "sqlite" -> """
                CREATE TABLE IF NOT EXISTS plot_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    plot_id INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    actor_uuid TEXT NOT NULL,
                    timestamp TEXT NOT NULL
                );
            """.trimIndent()

            "postgresql" -> """
                CREATE TABLE IF NOT EXISTS plot_logs (
                    id SERIAL PRIMARY KEY,
                    plot_id INTEGER NOT NULL,
                    action VARCHAR(255) NOT NULL,
                    actor_uuid VARCHAR(36) NOT NULL,
                    timestamp BIGINT NOT NULL
                );
            """.trimIndent()

            "h2" -> """
                CREATE TABLE IF NOT EXISTS plot_logs (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    plot_id INT NOT NULL,
                    action VARCHAR(255) NOT NULL,
                    actor_uuid VARCHAR(36) NOT NULL,
                    timestamp TEXT NOT NULL
                );
            """.trimIndent()

            else -> """
                CREATE TABLE IF NOT EXISTS plot_logs (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    plot_id INT NOT NULL,
                    action VARCHAR(255) NOT NULL,
                    actor_uuid VARCHAR(36) NOT NULL,
                    timestamp BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """.trimIndent()
        }
        add(createPlotLogsTable)

    }
}
