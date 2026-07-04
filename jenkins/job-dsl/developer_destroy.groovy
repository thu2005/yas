pipelineJob('developer_destroy') {
    description('Delete a developer test namespace created by developer_build.')
    authenticationToken('yas-destroy-token')

    logRotator {
        numToKeep(20)
    }

    parameters {
        stringParam('TARGET_NAMESPACE', '', 'Namespace to delete, for example test-user-tax')
    }

    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url('https://github.com/thu2005/yas.git')
                    }
                    branches('*/devops-cd')
                    extensions {
                        cloneOptions {
                            shallow(true)
                            depth(1)
                            noTags(true)
                            timeout(30)
                        }
                    }
                }
            }
            scriptPath('Jenkinsfile.destroy')
            lightweight(true)
        }
    }
}
