package com.xcwk.scalehub.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * auth / guide 接入层的流程推进表（T-接入波）。
 *
 * 只钉纯函数 [authFlowStepReduce]：Compose 侧只是 `state = reduce(state, event)`，
 * 推进口径在这里穷举，避免「回调没接上」这类只能靠手点发现的问题。
 */
class AuthFlowRouteTest {

    @Test
    fun `登录方式入口的四条分支各自进入对应屏幕`() {
        val cases = mapOf(
            AuthFlowEvent.OpenEmailLogin to AuthFlowStep.EmailLogin,
            AuthFlowEvent.OpenGuest to AuthFlowStep.Guest,
            AuthFlowEvent.OpenPasswordReset to AuthFlowStep.PasswordReset,
            AuthFlowEvent.OpenRecovery to AuthFlowStep.Recovery,
        )
        cases.forEach { (event, expected) ->
            assertThat(authFlowStepReduce(AuthFlowStep.Entry, event)).isEqualTo(expected)
        }
    }

    @Test
    fun `四条支线的成功事件统一推进到权限说明再进资料完善`() {
        val credentialSteps = listOf(
            AuthFlowStep.EmailLogin,
            AuthFlowStep.Guest,
            AuthFlowStep.PasswordReset,
            AuthFlowStep.Recovery,
        )
        credentialSteps.forEach { step ->
            val afterAuth = authFlowStepReduce(step, AuthFlowEvent.Authenticated)
            assertThat(afterAuth).isEqualTo(AuthFlowStep.Permission)
            assertThat(authFlowStepReduce(afterAuth, AuthFlowEvent.PermissionPassed))
                .isEqualTo(AuthFlowStep.Profile)
        }
    }

    @Test
    fun `重复上报登录成功不会越过权限说明`() {
        val once = authFlowStepReduce(AuthFlowStep.EmailLogin, AuthFlowEvent.Authenticated)
        assertThat(authFlowStepReduce(once, AuthFlowEvent.Authenticated)).isEqualTo(AuthFlowStep.Permission)
        assertThat(authFlowStepReduce(AuthFlowStep.Entry, AuthFlowEvent.Authenticated))
            .isEqualTo(AuthFlowStep.Entry)
    }

    @Test
    fun `权限事件只在权限说明页有效`() {
        assertThat(authFlowStepReduce(AuthFlowStep.Entry, AuthFlowEvent.PermissionPassed))
            .isEqualTo(AuthFlowStep.Entry)
        assertThat(authFlowStepReduce(AuthFlowStep.EmailLogin, AuthFlowEvent.PermissionPassed))
            .isEqualTo(AuthFlowStep.EmailLogin)
        assertThat(authFlowStepReduce(AuthFlowStep.Permission, AuthFlowEvent.PermissionPassed))
            .isEqualTo(AuthFlowStep.Profile)
        assertThat(authFlowStepReduce(AuthFlowStep.Profile, AuthFlowEvent.PermissionPassed))
            .isEqualTo(AuthFlowStep.Profile)
    }

    @Test
    fun `任何步骤返回都回到登录方式入口`() {
        AuthFlowStep.values().forEach { step ->
            assertThat(authFlowStepReduce(step, AuthFlowEvent.BackToEntry))
                .isEqualTo(AuthFlowStep.Entry)
        }
    }

    @Test
    fun `完整正向路径可从头走到资料完善`() {
        var step = AuthFlowStep.Entry
        listOf(
            AuthFlowEvent.OpenEmailLogin,
            AuthFlowEvent.Authenticated,
            AuthFlowEvent.PermissionPassed,
        ).forEach { step = authFlowStepReduce(step, it) }
        assertThat(step).isEqualTo(AuthFlowStep.Profile)
    }
}
