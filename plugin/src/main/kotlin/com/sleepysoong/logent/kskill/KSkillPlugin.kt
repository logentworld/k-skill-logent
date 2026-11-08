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

    override fun promptSection(): String =
        "k-skill은 browser-use와 computer-use를 필수로 사용해요. 브라우저 작업은 정식 browser_use로, " +
            "픽셀·스크린샷 확인은 computer_use로 실행해요. 원문의 Playwright CLI·Python helper는 " +
            "proot-cdp로 이미 실행 중인 Chromium에 붙여 실행하고, skill read 본문의 Logent 실행 환경 규칙을 " +
            "원문의 CLI-first·helper 필수 실행·완료 기준의 특정 실행기 요구보다 우선해요."

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
        return Result.success(bounded(body, BROWSER_RULES))
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

    private fun bounded(text: String, prefix: String = ""): String {
        val available = MAX_TEXT - prefix.length
        if (text.length <= available) return if (prefix.isEmpty()) text else prefix + text
        return buildString(MAX_TEXT) {
            append(prefix)
            append(text, 0, available - TRUNCATED.length)
            append(TRUNCATED)
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val KSKILL_PACKAGE_VERSION = "0.11.1"
        const val READ_TIMEOUT_SECONDS = 180
        const val MAX_TEXT = 24_000
        const val NPX_MISSING_EXIT = 69
        const val NODE_OLD_EXIT = 68
        const val NODE_MISSING_EXIT = 70
        const val TRUNCATED = "\n\n…스킬 출력 한도에서 잘렸어요."
        val BROWSER_RULES = """
            # Logent 실행 환경 규칙

            이 규칙은 k-skill의 모든 스킬에 적용하며 아래 원문의 브라우저 실행 지시보다 우선해요.
            - 브라우저 작업의 기본은 필수 의존 플러그인 browser-use의 browser_use 도구예요. 런타임·사용자 세션·탭은 browser-use 한 곳에서 관리해요.
            - 픽셀·스크린샷 확인은 필수 의존 플러그인 computer-use의 computer_use screenshot을 써요.
            - 원문 스킬이 Playwright 기반 CLI나 Python helper로 직접 브라우저를 띄우면 Termux에서는 playwright import가 android 플랫폼을 거부해 실패해요. 기기에 proot-cdp가 있으면 `proot-cdp npx --yes <cli> …` 또는 `proot-cdp python3 <script> …`로 실행해 이미 실행 중인 Chromium(CDP 9222)에 붙여요.
            - proot-cdp는 분리된 컨텍스트만 만들고 사용자 탭·프로필을 닫지 않아요. 실행이 끝나면 생성한 페이지만 정리해요.
            - proot-cdp가 없거나 CDP 연결에 실패하면 browser_use로 공식 웹 화면을 관찰하고, 그것도 안 되면 그대로 알려요.
            - Playwright 브라우저 설치(playwright install), 플랫폼 위장, BrowserOS·Aside·CloakBrowser, 원격 Playwright 실행 서비스는 쓰지 않아요.
            - 원문의 URL·검색 조건·데이터 의미·출력·승인 경계는 유지해요. CLI-first·helper 필수 실행·완료 기준의 특정 실행기 요구는 이 규칙으로 대체하고, 실제로 얻지 않은 결과를 만들었다고 보고하지 않아요.
            - 공개 데이터의 직접 HTTP 조회·로컬 파일 처리는 그대로 써요. CLI가 조회에도 브라우저를 요구하면 proot-cdp를 먼저 쓰고, 없으면 browser_use로 관찰해요.
            - browser_use를 쓸 때는 browser-use의 공식 스킬·도구 지침을 먼저 읽어요. taskSpace로 작업 공간을 만들고 task.page("p1")로 페이지를 얻어요. spaceId·page label을 출력해 다음 호출에서 같은 공간을 다시 열어요.
            - goto 뒤 snapshot으로 관찰하고 최신 ref·의미 기반 locator로 조작해요. DOM 코드는 page.evaluate 안에서만 실행하고 결과는 console.log로 출력해요. 원문의 Playwright 전용 API를 ego-browser API로 가정하지 않아요.
            - 파일·다운로드·내보내기는 현재 Logent 작업 디렉터리 아래 절대 경로를 써요.
            - 로그인·본인인증·CAPTCHA·전자서명은 사용자에게 넘겨요. 결제·예약·메시지 전송·취소·최종 제출은 정확한 대상과 효과에 대한 사용자 승인 없이 실행하지 않아요.
            - 사용자 탭·다른 작업 공간·프로필을 임의로 선택하거나 닫지 않아요. 성공한 작업만 task.finish({keep: []})로 정리해요.

            ## 원본 스킬 지침

        """.trimIndent() + "\n\n"
    }
}
