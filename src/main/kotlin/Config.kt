package ls.pgutil

fun String?.splitToList(): List<String>? {
    return this?.split("\\s".toRegex())?.takeIf { it.isNotEmpty() }
}

fun createConfigFromEnv(): Config {
    return Config(
        dockerImages = System.getenv("SETUP_IMAGES").splitToList()
            ?: throw IllegalArgumentException("SETUP_IMAGES not defined"),
        dbUri = System.getenv("DB_URI") ?: "postgres:postgres@localhost:5432/postgres",
        pgImage = System.getenv("PG_IMAGE") ?: "lovelysystems/docker-postgres:0.1.0",
        migraImage = System.getenv("PG_IMAGE") ?: "lovelysystems/migra:1.0.1597374790",
        schemas = System.getenv("SCHEMAS").splitToList()
    )
}

data class Config(
    val dockerImages: List<String>,
    val schemas: List<String>? = null,
    val dbUri: String = "postgres:postgres@host.docker.internal:35432/wla",
    val pgImage: String = "lovelysystems/docker-postgres:dev",
    val migraImage: String = "lovelysystems/migra:dev",
    val dbName: String = dbUri.split('/').last()
)
