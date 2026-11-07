package com.sleepysoong.logent.kskill

import com.sleepysoong.logent.plugin.PluginSkillReadContext
import com.sleepysoong.logent.plugin.ProcessResult
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** upstream skill.json에서 만든 k-skill 카탈로그와 npx 읽기 계약을 확인해요. */
class KSkillPluginTest {
    private val assets: Path = Path.of(
        requireNotNull(System.getProperty("logent.plugin.assets")) { "logent.plugin.assets가 필요해요." },
    )

    @Test
    fun `플러그인은 생성된 카탈로그의 모든 스킬을 노출한다`() {
        val plugin = KSkillPlugin()
        assertEquals("com.sleepysoong.logent.k-skill", plugin.id)
        assertEquals("k-skill", plugin.name)
        assertEquals(BuildConfig.PLUGIN_VERSION, plugin.version)
        assertEquals(4, plugin.apiVersion)

        val skills = plugin.skills()
        assertTrue(skills.isNotEmpty())
        assertEquals(skills.size, skills.map { it.name }.toSet().size)
        assertTrue(skills.all { !it.promptIndex })
        assertTrue(skills.all { it.description.length <= 500 })
        assertEquals(KSkillCatalog.entries.size, skills.size)
    }

    @Test
    fun `upstream 메타데이터에서 카탈로그 자산과 내장 목록이 같다`() {
        val catalog = Files.readString(assets.resolve("plugins/k-skill/catalog.json"))
        val names = Regex("\"name\"\\s*:\\s*\"([a-z0-9][a-z0-9-]{1,63})\"")
            .findAll(catalog)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(KSkillCatalog.entries.map { it.name }, names)
        assertTrue(Files.isRegularFile(assets.resolve("plugins/k-skill/LICENSE")))
    }

    @Test
    fun `생성 카탈로그는 upstream skilljson과 같은 이름을 담는다`() {
        val upstreamRoot = Path.of(
            requireNotNull(System.getProperty("logent.upstream.root")) { "logent.upstream.root가 필요해요." },
        )
        val upstreamNames = Files.list(upstreamRoot).use { paths ->
            paths.filter { Files.isDirectory(it) }
                .map { it.resolve("skill.json") }
                .filter { Files.isRegularFile(it) }
                .map { path ->
                    Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
                        .find(Files.readString(path))
                        ?.groupValues
                        ?.get(1)
                        ?: error("name이 없어요: $path")
                }
                .sorted()
                .toList()
        }
        assertEquals(upstreamNames, KSkillCatalog.entries.map { it.name }.sorted())
        assertEquals(KSkillCatalog.entries.map { it.name }.sorted(), KSkillPlugin().skills().map { it.name }.sorted())
    }

    @Test
    fun `읽기는 core와 같은 npx 명령을 셸로 실행한다`() = runBlocking {
        var command = ""
        val host = FakePluginHost { value, timeout ->
            command = value
            assertEquals(180, timeout)
            Result.success(ProcessResult(0, "  스킬 본문  ", truncated = false))
        }
        val skill = KSkillPlugin().skills().first()
        assertFalse(skill.promptIndex)
        val body = requireNotNull(skill.read)(
            PluginSkillReadContext(host = host),
        ).getOrThrow()
        assertEquals("스킬 본문", body)
        assertTrue(command.contains("npx --yes @nomadamas/k-skill@0.11.1 instruct '${skill.name}'"))
        assertTrue(command.contains("command -v npx"))
        assertTrue(command.contains("DISABLE_TELEMETRY=1"))
    }

    @Test
    fun `npx 누락은 실행 가능한 오류로 돌려준다`() = runBlocking {
        val host = FakePluginHost { _, _ -> Result.success(ProcessResult(69, "NPX_MISSING", false)) }
        val error = requireNotNull(KSkillPlugin().skills().first().read)(
            PluginSkillReadContext(host = host),
        ).exceptionOrNull()
        assertEquals(
            "npx가 없어요. Termux에서 pkg install nodejs로 Node.js와 npx를 설치해 주세요.",
            error?.message,
        )
    }
}
