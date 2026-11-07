package com.sleepysoong.logent.kskill

import com.sleepysoong.logent.plugin.LogentPlugin
import com.sleepysoong.logent.plugin.PluginSkill
import com.sleepysoong.logent.plugin.PluginSkillReadContext

/**
 * upstream k-skill 카탈로그의 스킬만 제공해요.
 *
 * 이름·설명은 upstream 각 스킬의 `skill.json`에서 생성해 DEX에 넣고, 본문은 npm의 고정 패키지
 * 진입점을 Node.js로 읽어 가져와요. Android에 없는 Unix shebang 경로에 기대지 않아요.
 */
class KSkillPlugin : LogentPlugin {
    override val id: String = "com.sleepysoong.logent.k-skill"
    override val name: String = "k-skill"
    override val version: String = BuildConfig.PLUGIN_VERSION

    private val remoteSkills: List<PluginSkill> = KSkillCatalog.entries.map { entry ->
        PluginSkill(
            name = entry.name,
            description = entry.description,
            source = "k-skill",
            // k-skill 본문은 Termux npx로 실행해 가져오므로 시스템 프롬프트 인덱스에 넣지 않아요.
            promptIndex = false,
            read = { context -> readRemote(entry.name, context) },
        )
    }

    override fun skills(): List<PluginSkill> = remoteSkills

    private suspend fun readRemote(name: String, context: PluginSkillReadContext): Result<String> {
        if (context.path != null) {
            return Result.failure(IllegalArgumentException("k-skill은 번들 파일 경로를 지원하지 않아요: $name"))
        }
        if (context.offset != 0) {
            return Result.failure(IllegalArgumentException("k-skill 본문은 한 번에 읽어요."))
        }
        val host = context.host
        val result = host.shell(remoteCommand(name), READ_TIMEOUT_SECONDS).getOrElse { error ->
            return Result.failure(
                IllegalStateException(
                    "Termux 셸을 실행하지 못했어요: ${error.message ?: error.javaClass.simpleName}",
                    error,
                ),
            )
        }
        if (!result.success) return Result.failure(IllegalStateException(shellFailure(name, result)))
        val body = result.output.trim()
        if (body.isEmpty()) {
            return Result.failure(IllegalStateException("k-skill이 스킬 '$name'의 내용을 비워서 돌려줬어요."))
        }
        return Result.success(bounded(body))
    }

    private fun remoteCommand(name: String): String = buildString {
        append("export PATH=/data/data/com.termux/files/usr/bin:\$PATH\n")
        append("command -v npx >/dev/null 2>&1 || { echo NPX_MISSING; exit $NPX_MISSING_EXIT; }\n")
        append("command -v node >/dev/null 2>&1 || { echo NODE_MISSING; exit $NODE_MISSING_EXIT; }\n")
        append("node -e 'const v=process.versions.node.split(\".\").map(Number); ")
        append("if(v[0]<22||(v[0]===22&&v[1]<20)) process.exit($NODE_OLD_EXIT)' || ")
        append("{ echo NODE_OLD; exit $NODE_OLD_EXIT; }\n")
        append("export DISABLE_TELEMETRY=1 DO_NOT_TRACK=1\n")
        val invocation = "exec node \"\$(command -v k-skill)\" instruct ${shellQuote(name)}"
        append("npx --yes --package=@nomadamas/k-skill@$KSKILL_PACKAGE_VERSION --call ")
        append(shellQuote(invocation)).append('\n')
    }

    private fun shellFailure(name: String, result: com.sleepysoong.logent.plugin.ProcessResult): String = when (result.exitCode) {
        NPX_MISSING_EXIT -> "npx가 없어요. Termux에서 pkg install nodejs로 Node.js와 npx를 설치해 주세요."
        NODE_OLD_EXIT -> "k-skill은 Node.js 22.20 이상이 필요해요. Termux에서 pkg upgrade nodejs로 올려 주세요."
        NODE_MISSING_EXIT -> "Node.js가 없어요. Termux에서 pkg install nodejs로 설치해 주세요."
        else -> {
            val output = result.output.trim().ifEmpty { "출력이 없어요." }
            bounded("k-skill '$name' 읽기에 실패했어요(exit ${result.exitCode}).\n$output")
        }
    }

    private fun bounded(text: String): String {
        if (text.length <= MAX_TEXT) return text
        val marker = "\n\n…스킬 출력 한도에서 잘렸어요."
        return text.take(MAX_TEXT - marker.length) + marker
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val KSKILL_PACKAGE_VERSION = "0.11.1"
        const val READ_TIMEOUT_SECONDS = 180
        const val MAX_TEXT = 24_000
        const val NPX_MISSING_EXIT = 69
        const val NODE_OLD_EXIT = 68
        const val NODE_MISSING_EXIT = 70
    }
}
