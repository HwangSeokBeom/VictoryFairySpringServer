package com.victoryfairy.server.tools

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

private val applicationTables =
    listOf(
        "attendance_logs",
        "community_blocks",
        "community_posts",
        "community_reports",
        "kbo_games",
        "preferences",
        "user_profiles",
    )

fun main() {
    val sourcePath = requiredEnvironment("VF_H2_SOURCE_PATH")
    val postgresURL = requiredEnvironment("VF_POSTGRES_URL")
    val postgresUser = requiredEnvironment("VF_POSTGRES_USER")
    val postgresPassword = requiredEnvironment("VF_POSTGRES_PASSWORD")
    val acknowledgement = requiredEnvironment("VF_DATA_MIGRATION_ACK")

    require(acknowledgement == "EMPTY_LOCAL_REHEARSAL") {
        "VF_DATA_MIGRATION_ACK must explicitly acknowledge an empty local rehearsal target."
    }
    require(isLoopbackPostgres(postgresURL)) {
        "This preparation tool refuses non-loopback PostgreSQL targets."
    }

    val sourceFile = Path.of(sourcePath).toAbsolutePath().normalize()
    require(sourceFile.fileName.toString().endsWith(".mv.db")) {
        "VF_H2_SOURCE_PATH must point to an H2 .mv.db file."
    }
    require(Files.isRegularFile(sourceFile)) {
        "The H2 source file does not exist."
    }

    val sourceBasePath = sourceFile.toString().removeSuffix(".mv.db")
    val sourceURL =
        "jdbc:h2:file:$sourceBasePath;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;ACCESS_MODE_DATA=r;IFEXISTS=TRUE"

    DriverManager.getConnection(sourceURL, "sa", "").use { source ->
        DriverManager.getConnection(postgresURL, postgresUser, postgresPassword).use { target ->
            migrate(source, target)
        }
    }
}

private fun migrate(source: Connection, target: Connection) {
    target.autoCommit = false
    try {
        requireTargetIsEmpty(target)

        for (table in applicationTables) {
            val copied = copyTable(source, target, table)
            println("Migrated $table rows=$copied")
        }

        target.createStatement().use { statement ->
            statement.execute(
                """
                SELECT setval(
                    pg_get_serial_sequence('kbo_games', 'id'),
                    COALESCE((SELECT MAX(id) FROM kbo_games), 1),
                    EXISTS(SELECT 1 FROM kbo_games)
                )
                """.trimIndent(),
            )
        }
        target.commit()
        println("H2 to PostgreSQL rehearsal migration completed.")
    } catch (error: Throwable) {
        target.rollback()
        throw error
    }
}

private fun requireTargetIsEmpty(target: Connection) {
    for (table in applicationTables) {
        target.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM ${quoted(table)}").use { result ->
                result.next()
                require(result.getLong(1) == 0L) {
                    "Target table $table is not empty; migration refused."
                }
            }
        }
    }
}

private fun copyTable(source: Connection, target: Connection, table: String): Long {
    source.createStatement().use { sourceStatement ->
        sourceStatement.executeQuery("SELECT * FROM ${quoted(table)}").use { rows ->
            val metadata = rows.metaData
            val columns = (1..metadata.columnCount).map(metadata::getColumnName)
            val quotedColumns = columns.joinToString(", ") { quoted(it) }
            val placeholders = columns.joinToString(", ") { "?" }
            val insert = "INSERT INTO ${quoted(table)} ($quotedColumns) VALUES ($placeholders)"
            var copied = 0L

            target.prepareStatement(insert).use { targetStatement ->
                while (rows.next()) {
                    columns.indices.forEach { index ->
                        targetStatement.setObject(index + 1, rows.getObject(index + 1))
                    }
                    targetStatement.addBatch()
                    copied += 1
                    if (copied % 500L == 0L) {
                        targetStatement.executeBatch()
                    }
                }
                targetStatement.executeBatch()
            }

            target.createStatement().use { verificationStatement ->
                verificationStatement.executeQuery("SELECT COUNT(*) FROM ${quoted(table)}").use { result ->
                    result.next()
                    check(result.getLong(1) == copied) {
                        "Row-count verification failed for $table."
                    }
                }
            }
            return copied
        }
    }
}

private fun quoted(identifier: String): String =
    "\"" + identifier.replace("\"", "\"\"") + "\""

private fun requiredEnvironment(name: String): String =
    System.getenv(name)?.takeIf(String::isNotBlank)
        ?: error("$name is required.")

private fun isLoopbackPostgres(url: String): Boolean =
    url.startsWith("jdbc:postgresql://127.0.0.1:")
        || url.startsWith("jdbc:postgresql://localhost:")
        || url.startsWith("jdbc:postgresql://[::1]:")
