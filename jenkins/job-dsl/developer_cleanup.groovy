pipelineJob('developer_cleanup') {
    description('Remove a developer test deployment from Kubernetes and free cluster resources.')
    logRotator {
        numToKeep(20)
    }

    parameters {
        choiceParam('PIPELINE_MODE', ['cleanup'], 'Pipeline mode for the cleanup job.')
        stringParam('TARGET_SERVICE', '', 'Service folder or Helm release name to remove.')
        stringParam('TARGET_NAMESPACE', 'yas', 'Kubernetes namespace that holds the developer deployment.')
    }

    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url('https://github.com/nashtech-garage/yas.git')
                    }
                    branches('*/main')
                }
            }
            scriptPath('Jenkinsfile')
            lightweight(true)
        }
    }
}
