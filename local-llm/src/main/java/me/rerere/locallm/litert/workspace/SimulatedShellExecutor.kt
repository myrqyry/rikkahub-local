package me.rerere.locallm.litert.workspace

import java.nio.charset.StandardCharsets

/**
 * Simulated shell executor (roadmap E5): executes a small set of common
 * file/text transforms against a [ShadowWorkspace] WITHOUT spawning arbitrary
 * native binaries. Returns stdout text and an exit code. Anything not expressible
 * in this safe subset is refused with exit code 126 (as POSIX "command not
 * executable").
 *
 * Supported: cat <path> | echo <text> > <path> | grep <needle> <path> |
 * cp <src> <dst> | mv <src> <dst> | rm <path> | sed s/a/b/ <path>
 */
class SimulatedShellExecutor(private val workspace: ShadowWorkspace) {

    suspend fun run(command: List<String>): WorkspaceCommandResult {
        if (command.isEmpty()) return WorkspaceCommandResult(0, "", "")
        return when (command[0]) {
            "cat" -> doCat(command)
            "echo" -> doEcho(command)
            "grep" -> doGrep(command)
            "cp" -> doCopy(command)
            "mv" -> doMove(command)
            "rm" -> doRm(command)
            "sed" -> doSed(command)
            else -> WorkspaceCommandResult(126, "", "simulated executor: unsupported command '${command[0]}'")
        }
    }

    private fun pathOf(token: String): String = token

    private suspend fun doCat(command: List<String>): WorkspaceCommandResult {
        if (command.size != 2) return WorkspaceCommandResult(2, "", "usage: cat <path>")
        val content = workspace.read(pathOf(command[1]))
        if (content == null) return WorkspaceCommandResult(1, "", "cat: ${command[1]}: No such file or directory")
        return WorkspaceCommandResult(0, content.toString(StandardCharsets.UTF_8), "")
    }

    private suspend fun doEcho(command: List<String>): WorkspaceCommandResult {
        // echo text > path  OR  echo text
        if (command.size == 1) return WorkspaceCommandResult(0, "\n", "")
        val gt = command.indexOf(">")
        if (gt in 1 until command.size - 1) {
            val text = command.subList(1, gt).joinToString(" ")
            workspace.writeText(pathOf(command[gt + 1]), text + "\n")
            return WorkspaceCommandResult(0, "", "")
        }
        return WorkspaceCommandResult(0, command.subList(1, command.size).joinToString(" ") + "\n", "")
    }

    private suspend fun doGrep(command: List<String>): WorkspaceCommandResult {
        if (command.size != 3) return WorkspaceCommandResult(2, "", "usage: grep <needle> <path>")
        val content = workspace.readText(pathOf(command[2])) ?: return WorkspaceCommandResult(1, "", "")
        val matches = content.lines().filter { it.contains(command[1]) }
        return WorkspaceCommandResult(if (matches.isEmpty()) 1 else 0, matches.joinToString("\n") + (if (matches.isNotEmpty()) "\n" else ""), "")
    }

    private suspend fun doCopy(command: List<String>): WorkspaceCommandResult {
        if (command.size != 3) return WorkspaceCommandResult(2, "", "usage: cp <src> <dst>")
        val content = workspace.read(pathOf(command[1])) ?: return WorkspaceCommandResult(1, "", "cp: ${command[1]}: No such file")
        workspace.write(pathOf(command[2]), content)
        return WorkspaceCommandResult(0, "", "")
    }

    private suspend fun doMove(command: List<String>): WorkspaceCommandResult {
        if (command.size != 3) return WorkspaceCommandResult(2, "", "usage: mv <src> <dst>")
        val content = workspace.read(pathOf(command[1])) ?: return WorkspaceCommandResult(1, "", "mv: ${command[1]}: No such file")
        workspace.write(pathOf(command[2]), content)
        workspace.delete(pathOf(command[1]))
        return WorkspaceCommandResult(0, "", "")
    }

    private suspend fun doRm(command: List<String>): WorkspaceCommandResult {
        if (command.size != 2) return WorkspaceCommandResult(2, "", "usage: rm <path>")
        workspace.delete(pathOf(command[1]))
        return WorkspaceCommandResult(0, "", "")
    }

    private suspend fun doSed(command: List<String>): WorkspaceCommandResult {
        if (command.size != 3 || !command[1].startsWith("s/")) {
            return WorkspaceCommandResult(2, "", "usage: sed s/from/to/ <path>")
        }
        val spec = command[1].removePrefix("s/")
        val parts = spec.trimEnd('/').split("/")
        if (parts.size != 2 || parts[0].isEmpty()) return WorkspaceCommandResult(2, "", "invalid sed expression")
        val content = workspace.readText(pathOf(command[2])) ?: return WorkspaceCommandResult(1, "", "sed: ${command[2]}: No such file")
        workspace.writeText(pathOf(command[2]), content.replace(parts[0], parts[1]))
        return WorkspaceCommandResult(0, "", "")
    }
}
