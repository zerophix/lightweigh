package com.xcwk.scalehub

/**
 * 守卫负例夹具 —— 组合根白名单（P2）与表现层桥面白名单（P1）的违例目标。
 * 仅存在于测试源码；生产代码引用它会被守卫判红（正是本类的用途）。
 */
@Suppress("unused")
class RootSneakFixture {
    fun peek(): Int = 1
}
