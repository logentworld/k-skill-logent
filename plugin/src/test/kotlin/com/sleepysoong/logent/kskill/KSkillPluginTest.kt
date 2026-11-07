package com.sleepysoong.logent.kskill

import com.sleepysoong.logent.plugin.PluginSkillReadContext
import com.sleepysoong.logent.plugin.ProcessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** 공통 실행 지침이 원문 읽기 실패를 성공으로 가리지 않는지 확인해요. */
class KSkillPluginTest {
    @Test
    fun `빈 원문을 공통 지침만 있는 성공으로 반환하지 않아요`() = runBlocking {
        val host = FakePluginHost { _, _ -> Result.success(ProcessResult(0, " \n", false)) }
        val skill = KSkillPlugin().skills().first { it.name == "bunjang-search" }
        val result = requireNotNull(skill.read)(PluginSkillReadContext(host = host))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `실패한 프로세스의 부분 출력을 완성된 스킬로 반환하지 않아요`() = runBlocking {
        val host = FakePluginHost { _, _ ->
            Result.success(ProcessResult(127, "# 일부만 읽은 스킬 본문\n", false))
        }
        val skill = KSkillPlugin().skills().first { it.name == "foresttrip-vacancy" }
        val result = requireNotNull(skill.read)(PluginSkillReadContext(host = host))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

}
