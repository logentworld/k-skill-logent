package com.sleepysoong.logent.kskill

import com.sleepysoong.logent.plugin.PluginSkillReadContext
import com.sleepysoong.logent.plugin.ProcessResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assume.assumeTrue

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
    fun `Unix 인터프리터가 없는 npm 진입점도 Node로 스킬 본문을 읽어요`() = runBlocking {
        val available = ProcessBuilder("bash", "-c", "command -v node && command -v npx")
            .redirectErrorStream(true).start()
        assumeTrue(available.inputStream.bufferedReader().readText(), available.waitFor() == 0)
        val upstreamRoot = Path.of(requireNotNull(System.getProperty("logent.upstream.root")))
        val directory = createTempDirectory("kskill-node-entry")
        try {
            Files.writeString(directory.resolve("package.json"), """{"name":"kskill-read-test","version":"1.0.0"}""")
            val module = directory.resolve("node_modules/@nomadamas/k-skill")
            Files.createDirectories(module.resolve("bin"))
            Files.writeString(
                module.resolve("package.json"),
                """{"name":"@nomadamas/k-skill","version":"0.11.1","bin":{"k-skill":"bin/k-skill.js"}}""",
            )
            val source = upstreamRoot.resolve("packages/k-skill-cli/bin/k-skill.js")
                .toString().replace("\\", "\\\\").replace("'", "\\'")
            val entry = module.resolve("bin/k-skill.js")
            Files.writeString(entry, "#!/logent-test/없는-인터프리터\nrequire('$source');\n")
            check(entry.toFile().setExecutable(true))
            val bin = Files.createDirectories(directory.resolve("node_modules/.bin"))
            Files.createSymbolicLink(bin.resolve("k-skill"), bin.relativize(entry))
            val host = FakePluginHost { command, _ ->
                runCatching {
                    val output = directory.resolve("output.txt").toFile()
                    val process = ProcessBuilder("bash", "-c", command)
                        .directory(directory.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(output)
                        .apply {
                            environment()["npm_config_offline"] = "true"
                            environment()["npm_config_cache"] = directory.resolve("cache").toString()
                            environment().remove("LD_PRELOAD")
                        }
                        .start()
                    try {
                        check(process.waitFor(30, TimeUnit.SECONDS)) { "본문 읽기 시간이 초과됐어요." }
                        ProcessResult(process.exitValue(), output.readText(), truncated = false)
                    } finally {
                        process.destroyForcibly()
                    }
                }
            }
            val skill = KSkillPlugin().skills().first { it.name == "bunjang-search" }
            val body = requireNotNull(skill.read)(PluginSkillReadContext(host = host)).getOrThrow()
            assertTrue(body, body.startsWith("# bunjang-search — assembled instructions"))
            assertTrue(body, body.contains("npx --yes bunjang-cli --json search"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
