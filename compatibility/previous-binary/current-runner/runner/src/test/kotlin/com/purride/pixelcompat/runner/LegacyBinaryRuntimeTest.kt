package com.purride.pixelcompat.runner

import org.junit.Assert.assertEquals
import org.junit.Test

/** 运行时证明：只替换 engine AAR 后，旧消费者二进制仍能正常运行。 */
class LegacyBinaryRuntimeTest {
    /** 反射调用旧探针，使该 runner 永远不会重新编译其 engine 调用点。 */
    @Test
    fun oldRenderSpiBinaryRunsOnCurrentEngine() {
        /** 从预编译 AAR 中加载的旧消费者类。 */
        val probeClass = Class.forName("com.purride.pixelcompat.legacy.LegacyRenderSpiProbe")
        /** 字节码针对冻结版 engine 编译的静态无参方法。 */
        val probeMethod = probeClass.getMethod("run")
        /** 跨二进制边界返回的、只含基本类型/String 的行为摘要。 */
        val summary = probeMethod.invoke(null) as String

        assertEquals("create=1;update=2;first=true;second=true", summary)
    }
}
