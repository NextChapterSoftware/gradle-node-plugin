package com.github.gradle.node.pnpm.task

import com.github.gradle.node.task.AbstractTaskTest
import com.github.gradle.node.util.PlatformHelperKt

class PnpmTaskTest extends AbstractTaskTest {
    def "exec pnpm task"() {
        given:
        props.setProperty('os.name', 'Linux')
        nodeExtension.computedPlatform.set(PlatformHelperKt.parsePlatform("Linux", "x86_64", {}))

        def task = project.tasks.create('simple', PnpmTask)
        mockPlatformHelper(task)
        mockProjectApiHelperExec(task)
        task.args.set(['a', 'b'])
        task.environment.set(['a': '1'])
        task.ignoreExitValue.set(true)
        task.workingDir.set(projectDir)

        when:
        project.evaluate()
        task.exec()

        then:
        task.args.set(['a', 'b'])
        1 * execSpec.setIgnoreExitValue(true)
        1 * execSpec.setEnvironment({ it['a'] == '1' && containsPath(it) })
        1 * execSpec.setExecutable('pnpm')
        1 * execSpec.setArgs(['a', 'b'])
    }

    def "exec pnpm task (windows)"() {
        given:
        props.setProperty('os.name', 'Windows')
        nodeExtension.computedPlatform.set(PlatformHelperKt.parsePlatform("Windows", "x86_64", {}))

        def task = project.tasks.create('simple', PnpmTask)
        mockPlatformHelper(task)
        mockProjectApiHelperExec(task)
        task.args.set(['a', 'b'])
        task.environment.set(['a': '1'])
        task.ignoreExitValue.set(true)
        task.workingDir.set(projectDir)

        when:
        project.evaluate()
        task.exec()

        then:
        task.args.get() == ['a', 'b']
        1 * execSpec.setIgnoreExitValue(true)
        1 * execSpec.setEnvironment({ it['a'] == '1' && containsPath(it) })
        1 * execSpec.setExecutable('pnpm.cmd')
        1 * execSpec.setArgs(['a', 'b'])
    }

    def "exec pnpm task (download)"() {
        given:
        props.setProperty('os.name', 'Linux')
        nodeExtension.computedPlatform.set(PlatformHelperKt.parsePlatform("Linux", "x86_64", {}))
        nodeExtension.download.set(true)

        def task = project.tasks.create('simple', PnpmTask)
        mockPlatformHelper(task)
        mockProjectApiHelperExec(task)

        when:
        project.evaluate()
        task.exec()

        then:
        1 * execSpec.setIgnoreExitValue(false)
    }
}
