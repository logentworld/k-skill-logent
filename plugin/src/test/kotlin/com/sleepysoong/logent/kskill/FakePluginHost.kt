package com.sleepysoong.logent.kskill

import com.sleepysoong.logent.plugin.PluginHost
import com.sleepysoong.logent.plugin.ProcessResult

/** k-skill 본문 읽기에 필요한 셸 계약만 흉내 내요. */
internal class FakePluginHost(
    private val shellResult: (String, Int) -> Result<ProcessResult>,
) : PluginHost {
    override val appVersion: String = "test"
    override val apkPath: String = "/test.apk"

    override suspend fun shell(command: String, timeoutSeconds: Int): Result<ProcessResult> =
        shellResult(command, timeoutSeconds)

    override suspend fun ensureDesktop(): Result<Unit> = Result.success(Unit)

    override suspend fun readAsset(path: String): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("테스트에서는 자산을 읽지 않아요: $path"))

    override fun progress(message: String) = Unit
}
