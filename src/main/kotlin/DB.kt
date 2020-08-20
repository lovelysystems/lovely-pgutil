package ls.pgutil

import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.ToStringConsumer
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.utility.DockerImageName
import java.io.File

val LOGGER = KotlinLogging.logger {}

class MigraContainer(
    private val dbContainer: VanillaDBContainer,
    private val dbURI: String,
    dbName: String,
    imageName: String
) :
    GenericContainer<MigraContainer>(DockerImageName.parse(imageName)) {

    private val allowedExitCodes = setOf(0L, 2L)
    private val sourceURI = "postgres://postgres:postgres@postgres:5432/$dbName"

    override fun configure() {
        withCommand("tail", "-f", "/dev/null")
        withNetwork(dbContainer.network)
        super.configure()
    }

    fun diffFull(): String {
        val res = exec(
            "migra", "--with-privileges", "--unsafe",
            "postgres://$dbURI",
            sourceURI,
            allowedExitCodes = allowedExitCodes
        )
        return res.outText().trim() + '\n'
    }

    fun diffSchema(schema: String): String {
        val res = exec(
            "migra", "--with-privileges", "--unsafe",
            "--schema", schema,
            "postgres://$dbURI",
            sourceURI,
            allowedExitCodes = allowedExitCodes
        )
        return res.outText().trim() + '\n'
    }
}

class VanillaDBContainer(imageName: String) :
    GenericContainer<VanillaDBContainer>(DockerImageName.parse(imageName)) {

    override fun configure() {
        withNetworkAliases("postgres")
        withEnv(
            mapOf(
                "PGUSER" to "postgres",
                "POSTGRES_PASSWORD" to "postgres"
            )
        )
        withNetwork(Network.newNetwork()).apply {
            setWaitStrategy(LogMessageWaitStrategy().withRegEx(".*database system is ready to accept connections.*"))
        }

        super.configure()
    }
}

class DBSetupContainer(private val dbContainer: VanillaDBContainer, private val imageName: String) :
    GenericContainer<DBSetupContainer>(DockerImageName.parse(imageName)) {

    private val logConsumer = ToStringConsumer()

    override fun configure() {
        withCommand("setup_db")
        withEnv(
            mapOf(
                "PGUSER" to "postgres",
                "PGHOST" to "postgres",
                "PGPASSWORD" to "postgres",
                "PGDATABASE" to "postgres",
                "PGPORT" to "5432"
            )
        )
        withNetwork(dbContainer.network)
        withLogConsumer(logConsumer)
        withStartupCheckStrategy(
            OneShotStartupCheckStrategy()
        )
        super.configure()
    }

    fun run(): String {
        LOGGER.info { "starting db setup $imageName" }
        try {
            start()
        } catch (e: Throwable) {
            error("start of $imageName failed ${logConsumer.toUtf8String()}")
        }
        LOGGER.info { "finished db setup $imageName" }
        return logConsumer.toUtf8String()
    }
}

class DBController(private val config: Config) {

    private val pgContainer = VanillaDBContainer(config.pgImage)
    private val migraContainer = MigraContainer(pgContainer, config.dbUri, config.dbName, config.migraImage)
    private val setupContainers = config.dockerImages.map { DBSetupContainer(pgContainer, it) }

    fun diff(outDir: File) {
        pgContainer.start()

        // pgContainer.execInContainer()

        migraContainer.start()
        try {
            for (setupContainer in setupContainers) {
                setupContainer.run()
            }
            if (config.schemas == null) {
                val sql = migraContainer.diffFull()
                val outFile = outDir.resolve("full_db.sql")
                LOGGER.info { "writing full diff to $outFile" }
                outFile.writeText(sql)
            } else {
                for (schemaName in config.schemas) {
                    val sql = migraContainer.diffSchema(schemaName)
                    if (sql.isBlank()) {
                        LOGGER.info { "no diff for schema $schemaName" }
                        continue
                    } else {
                        val outFile = outDir.resolve("$schemaName.sql")
                        LOGGER.info { "writing schema diff for $schemaName to $outFile" }
                        outDir.resolve("$schemaName.sql").writeText(sql)
                    }
                }
            }
        } finally {
            migraContainer.stop()
            pgContainer.stop()
        }
    }
}

