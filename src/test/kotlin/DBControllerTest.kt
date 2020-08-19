import io.kotest.matchers.file.shouldContainNFiles
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeBlank
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path

internal class DBControllerTest {

    /**
     * This is an example db setup image, it needs to provide an executable called `db_setup`
     *
     * Note: this image creates both the source and the target database
     * normally only the source would be created, however in this test we use
     * the same server as target with a different db, so we do not need to start
     * another database container
     *
     */
    val dbSetupImage = ImageFromDockerfile().withFileFromString(
        "Dockerfile",
        """
                FROM alpine:3.11.5
                RUN apk --update --no-cache add postgresql-client=12.2-r0
                COPY setup_db /usr/bin/
                RUN chmod 755 /usr/bin/setup_db
                CMD true
            """.trimIndent()
    ).withFileFromString(
        "setup_db",
        """
                #!/bin/sh
                set -e
                createdb vanilla
                createdb target
                psql -c 'create schema internal create table t(x int);' vanilla
            """.trimIndent()
    )

    @Test
    fun `without schemas a full diff is generated`(@TempDir tmpPath: Path) {

        val imageName = dbSetupImage.get()
        val outDir = tmpPath.toFile()
        val db = DBController(
            Config(
                dockerImages = listOf(imageName),
                pgImage = "lovelysystems/docker-postgres:dev",
                dbUri = "postgres:postgres@postgres:5432/target",
                dbName = "vanilla"
            )
        )
        db.diff(outDir)
        outDir shouldContainNFiles 1
        outDir.resolve("full_db.sql").readText() shouldBe """
            create schema if not exists "internal";

            create table "internal"."t" (
                "x" integer
            );
            
            """.trimIndent()
    }

    @Test
    fun `a file gets generated for each schema`(@TempDir tmpPath: Path) {

        val imageName = dbSetupImage.get()
        val outDir = tmpPath.toFile()
        val db = DBController(
            Config(
                dockerImages = listOf(imageName),
                pgImage = "lovelysystems/docker-postgres:dev",
                dbUri = "postgres:postgres@postgres:5432/target",
                dbName = "vanilla",
                schemas = listOf("public", "internal"),
            )
        )
        db.diff(outDir)
        outDir shouldContainNFiles 2

        // no changes in schema public
        outDir.resolve("public.sql").readText().shouldBeBlank()

        // changes for schema internal is in its own file
        outDir.resolve("internal.sql").readText() shouldBe """
            create schema if not exists "internal";

            create table "internal"."t" (
                "x" integer
            );
            
            """.trimIndent()
    }
}