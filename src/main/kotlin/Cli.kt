import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file

class PGUtil : NoOpCliktCommand()

/**
 * Noop command as root for subcommands, put common configs here
 */
open class DBCommand(help: String) : NoOpCliktCommand(help = help)

class Diff : DBCommand("Generate sql diffs into files") {

    private val outDir by argument(help = "the output directory where diff scripts are written to").file(
        mustExist = true,
        mustBeWritable = true,
        canBeFile = false
    )

    override fun run() {
        val config = createConfigFromEnv()
        val db = DBController(config)
        db.diff(outDir)
    }
}

fun main(args: Array<String>) = PGUtil().subcommands(Diff()).main(args)
