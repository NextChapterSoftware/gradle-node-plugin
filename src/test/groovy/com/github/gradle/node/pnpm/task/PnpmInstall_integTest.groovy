package com.github.gradle.node.pnpm.task

import com.github.gradle.AbstractIntegTest
import org.gradle.testkit.runner.TaskOutcome

class PnpmInstall_integTest
    extends AbstractIntegTest
{
    def 'install packages with pnpm (#gv.version)'()
    {
        given:
        gradleVersion = gv
        writeBuild( '''
            plugins {
                id 'com.github.node-gradle.node'
            }

            node {
                download = true
                workDir = file('build/node')
                pnpmWorkDir = file('build/pnpm')
            }
        ''' )
        writeEmptyPackageJson()
        writeFile("pnpm-lock.yaml", "lockfileVersion: '6.0'")

        when:
        def result = build( 'pnpmInstall' )

        then:
        result.task(":nodeSetup").outcome == TaskOutcome.SUCCESS
        result.task(":pnpmSetup").outcome == TaskOutcome.SUCCESS
        result.task(":pnpmInstall").outcome == TaskOutcome.SUCCESS

        when:
        result = build( 'pnpmInstall' )

        then:
        result.task(":nodeSetup").outcome == TaskOutcome.UP_TO_DATE
        result.task(":pnpmSetup").outcome == TaskOutcome.UP_TO_DATE
        // because pnpm-lock.yaml is created only when needed
        result.task(":pnpmInstall").outcome == TaskOutcome.UP_TO_DATE

        where:
        gv << GRADLE_VERSIONS_UNDER_TEST
    }

    def 'install packages with pnpm and postinstall task requiring pnpm and node (#gv.version)'()
    {
        given:
        gradleVersion = gv
        writeBuild( '''
            plugins {
                id 'com.github.node-gradle.node'
            }
            node {
                download = true
                workDir = file('build/node')
                pnpmWorkDir = file('build/pnpm')
            }
        ''' )
        writePackageJson(""" {
            "name": "example",
            "dependencies": {},
            "versionOutput" : "node --version",
            "postinstall" : "pnpm run versionOutput"
        }
        """)
        writeFile("pnpm-lock.yaml", "lockfileVersion: '6.0'")

        when:
        def result = build( 'pnpmInstall', '--info' )

        then:
        result.task(":nodeSetup").outcome == TaskOutcome.SUCCESS
        result.task(":pnpmSetup").outcome == TaskOutcome.SUCCESS
        result.task(":pnpmInstall").outcome == TaskOutcome.SUCCESS

        when:
        result = build( 'pnpmInstall' )

        then:
        result.task(":nodeSetup").outcome == TaskOutcome.UP_TO_DATE
        result.task(":pnpmSetup").outcome == TaskOutcome.UP_TO_DATE
        result.task(":pnpmInstall").outcome == TaskOutcome.UP_TO_DATE

        where:
        gv << GRADLE_VERSIONS_UNDER_TEST
    }

    def 'install packages with pnpm in different directory (#gv.version)'()
    {
        given:
        gradleVersion = gv
        writeBuild( '''
            plugins {
                id 'com.github.node-gradle.node'
            }

            node {
                download = true
                workDir = file('build/node')
                pnpmWorkDir = file('build/pnpm')
                nodeProjectDir = file('subdirectory')
            }
        ''' )
        writeFile( 'subdirectory/package.json', """{
            "name": "example",
            "dependencies": {
            }
        }""" )

        when:
        def result = build( 'pnpmInstall' )

        then:
        result.task( ':pnpmInstall' ).outcome == TaskOutcome.SUCCESS

        where:
        gv << GRADLE_VERSIONS_UNDER_TEST
    }

    def 'verify pnpm install inputs/outputs (#gv.version)'()
    {
        given:
        gradleVersion = gv
        writeBuild( '''
            plugins {
                id 'com.github.node-gradle.node'
            }

            node {
                download = true
                workDir = file('build/node')
                npmInstallCommand = 'install'
            }

            def lock = file('pnpm-lock.yaml')
            task verifyIO {
                def outputs = tasks.named("pnpmInstall").get().outputs.files
                def inputs = tasks.named("pnpmInstall").get().inputs.files

                doLast {
                    if (!outputs.contains(lock)) {
                        throw new RuntimeException("pnpm-lock.yaml is not in INSTALL'S outputs!")
                    }
                    if (inputs.contains(lock)) {
                        throw new RuntimeException("pnpm-lock.yaml is in INSTALL'S inputs!")
                    }
                }
            }
        ''' )
        writeEmptyPackageJson()
        writeFile('pnpm-lock.yaml', '')

        when:
        def result = buildTask( 'verifyIO' )

        then:
        result.outcome == TaskOutcome.SUCCESS

        where:
        gv << GRADLE_VERSIONS_UNDER_TEST
    }

    def 'verify output configuration (#gv.version)'() {
        given:
        gradleVersion = gv
        writeBuild('''
            plugins {
                id 'com.github.node-gradle.node'
            }

            node {
                download = true
                workDir = file('build/node')
                pnpmWorkDir = file('build/pnpm')
            }
        ''')
        writePackageJson("""
            {
              "name": "hello",
              "dependencies": {
                "mocha": "6.2.0"
              }
            }
        """)

        when:
        def result1 = build("pnpmInstall")

        then:
        result1.task(":pnpmInstall").outcome == TaskOutcome.SUCCESS

        when:
        def result2 = build("pnpmInstall")

        then:
        // Because pnpm-lock.yaml was created
        result2.task(":pnpmInstall").outcome == TaskOutcome.SUCCESS

        when:
        // Let's add a file in the node_modules directory
        writeFile("node_modules/mocha/newFile.txt", "hello")
        def result3 = build("pnpmInstall")

        then:
        // It should not make the build out-of-date
        result3.task(":pnpmInstall").outcome == TaskOutcome.UP_TO_DATE

        when:
        // Let's update a file in the node_modules directory
        createFile("node_modules/mocha/README.md").delete()
        writeFile("node_modules/mocha/README.md", "modified README")
        def result4 = build("pnpmInstall")

        then:
        // This time the build should not be up-to-date and the file could (but it's not) reset
        result4.task(":pnpmInstall").outcome == TaskOutcome.SUCCESS
        createFile("node_modules/mocha/README.md").text == "modified README"

        when:
        // Let's delete a file in the node_modules directory
        createFile("node_modules/mocha").delete()
        def result5 = build("pnpmInstall")

        then:
        // This time the build should not be up-to-date and the file should be reset
        result5.task(":pnpmInstall").outcome == TaskOutcome.SUCCESS
        createFile("node_modules/mocha/package.json").exists()

        where:
        gv << GRADLE_VERSIONS_UNDER_TEST
    }

    def 'verify output configuration when filtering node_modules output (#gv.version)'()
    {
        given:
        gradleVersion = gv
        writeBuild( '''
            plugins {
                id 'com.github.node-gradle.node'
            }

            node {
                download = true
                workDir = file('build/node')
            }

            pnpmInstall {
                nodeModulesOutputFilter { 
                    exclude("mocha")
                }
            }
        ''' )
        writePackageJson("""
            {
              "name": "hello",
              "dependencies": {
                "mocha": "6.2.0"
              }
            }
        """)

        when:
        createFile("node_modules").deleteDir()
        def result1 = build("pnpmInstall")

        then:
        result1.task(":pnpmInstall").outcome == TaskOutcome.SUCCESS

        when:
        // Let's add a file in the node_modules directory
        writeFile("node_modules/mocha/newFile.txt", "hello")
        def result2 = build("pnpmInstall")

        then:
        // It should make the build out-of-date
        result2.task(":pnpmInstall").outcome == TaskOutcome.SUCCESS

        when:
        // Let's update a file in the node_modules directory
        createFile("node_modules/mocha").delete()
        def result3 = build("pnpmInstall")

        then:
        // The build should still be up-to-date
        result3.task(":pnpmInstall").outcome == TaskOutcome.UP_TO_DATE

        when:
        // Let's delete an excluded file in the node_modules directory
        createFile("node_modules/mocha").delete()
        def result4 = build("pnpmInstall")

        then:
        // The build should still be up-to-date
        result4.task(":pnpmInstall").outcome == TaskOutcome.UP_TO_DATE

        where:
        gv << GRADLE_VERSIONS_UNDER_TEST
    }
}
