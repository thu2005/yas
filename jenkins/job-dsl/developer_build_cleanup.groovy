pipelineJob('developer_build_cleanup') {
    description('Reset GitOps image tags for YAS dev/staging and let Argo CD redeploy main images.')
    logRotator {
        numToKeep(20)
    }

    parameters {
        choiceParam('TARGET_ENV', ['dev', 'staging'], 'GitOps environment to reset')
        choiceParam('CLEANUP_MODE', ['RESET_TO_MAIN', 'SELECTIVE'], 'Reset all services or selected services only')

        booleanParam('RESET_PRODUCT', false, 'Reset product-service to main')
        booleanParam('RESET_INVENTORY', false, 'Reset inventory-service to main')
        booleanParam('RESET_SEARCH', false, 'Reset search-service to main')
        booleanParam('RESET_MEDIA', false, 'Reset media-service to main')
        booleanParam('RESET_CART', false, 'Reset cart-service to main')
        booleanParam('RESET_ORDER', false, 'Reset order-service to main')
        booleanParam('RESET_TAX', false, 'Reset tax-service to main')
        booleanParam('RESET_STOREFRONT_UI', false, 'Reset storefront-ui to main')
        booleanParam('RESET_STOREFRONT_BFF', false, 'Reset storefront-bff to main')
        booleanParam('RESET_BACKOFFICE_UI', false, 'Reset backoffice-ui to main')
        booleanParam('RESET_BACKOFFICE_BFF', false, 'Reset backoffice-bff to main')
        booleanParam('RESET_CUSTOMER', false, 'Reset customer-service to main')
        booleanParam('RESET_SAMPLEDATA', false, 'Reset sampledata to main')

        booleanParam('DRY_RUN', true, 'Show diff without pushing GitOps commit')
        booleanParam('CONFIRM', false, 'Required when DRY_RUN=false')
    }

    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url('https://github.com/thu2005/gitops-yas.git')
                        credentials('github-credentials')
                    }
                    branches('*/main')
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
            scriptPath('jenkins/Jenkinsfile.cleanup')
            lightweight(true)
        }
    }
}
