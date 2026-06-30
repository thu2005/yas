pipelineJob('developer_build') {
    description('Build, push to Docker Hub, update GitOps values, deploy a branch-specific service, and print the NodePort URL.')
    logRotator {
        numToKeep(20)
    }

    parameters {
        choiceParam('PIPELINE_MODE', ['developer_build'], 'Pipeline mode for the developer build job.')
        stringParam('TARGET_BRANCH', 'main', 'Branch to deploy for testing.')
        stringParam('BASE_BRANCH', 'main', 'Base branch used to detect changed service folders.')
        stringParam('TARGET_NAMESPACE', 'yas', 'Kubernetes namespace used for the developer deploy.')
        stringParam('DOCKER_HUB_NAMESPACE', 'your-dockerhub-namespace', 'Docker Hub namespace used for pushed images.')
        stringParam('GIT_REMOTE_URL', 'https://github.com/nashtech-garage/yas.git', 'Git remote used for checkout.')
        stringParam('GITOPS_BRANCH', '', 'Optional branch to push GitOps updates back to. Defaults to TARGET_BRANCH.')
        stringParam('DOCKERHUB_CREDENTIALS_ID', 'dockerhub-credentials', 'Jenkins credentials id for Docker Hub login.')
        stringParam('GIT_PUSH_CREDENTIALS_ID', 'github-credentials', 'Jenkins credentials id for Git push.')
        stringParam('GIT_COMMIT_USER_NAME', 'jenkins-bot', 'Git user name used for GitOps commits.')
        stringParam('GIT_COMMIT_USER_EMAIL', 'jenkins-bot@example.com', 'Git user email used for GitOps commits.')
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
