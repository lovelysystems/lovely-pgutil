import com.github.dockerjava.core.command.ExecStartResultCallback
import org.testcontainers.containers.GenericContainer
import java.io.ByteArrayOutputStream

class ExecResult(
    val exitCode: Long,
    private val stdout: ByteArrayOutputStream,
    private val stderr: ByteArrayOutputStream
) {
    fun errText(): String {
        stderr.flush()
        return String(stderr.toByteArray())
    }

    fun outText(): String {
        stdout.flush()
        return String(stdout.toByteArray())
    }
}

/**
 * special exec function, since testcontainers adds newlines to the result
 * see https://github.com/testcontainers/testcontainers-java/issues/1854
 */
fun GenericContainer<*>.exec(vararg command: String, allowedExitCodes: Collection<Long> = listOf(0L)): ExecResult {
    val outStream = ByteArrayOutputStream()
    val errStream = ByteArrayOutputStream()
    val execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
        .withAttachStdout(true).withAttachStderr(true).withCmd(*command).exec()
    val callback = ExecStartResultCallback(outStream, errStream)
    dockerClient.execStartCmd(execCreateCmdResponse.id).exec(callback)
        .awaitCompletion()
    val exitCode = dockerClient.inspectExecCmd(execCreateCmdResponse.id).exec().exitCodeLong
    val res = ExecResult(exitCode, outStream, errStream)
    if (!allowedExitCodes.contains(exitCode)) {
        error("exec failed with code:$exitCode error:\n${res.errText()}")
    }
    return res
}