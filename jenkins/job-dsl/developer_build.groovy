pipelineJob('developer_build') {
    description('Update GitOps image tags for YAS dev/staging and let Argo CD deploy the selected service branches.')
    logRotator {
        numToKeep(20)
    }

    parameters {
        choiceParam('TARGET_ENV', ['dev', 'staging'], 'GitOps environment to update')
        stringParam('PRODUCT_SERVICE_BRANCH', 'main', 'Branch for product-service')
        stringParam('CART_SERVICE_BRANCH', 'main', 'Branch for cart-service')
        stringParam('ORDER_SERVICE_BRANCH', 'main', 'Branch for order-service')
        stringParam('CUSTOMER_SERVICE_BRANCH', 'main', 'Branch for customer-service')
        stringParam('INVENTORY_SERVICE_BRANCH', 'main', 'Branch for inventory-service')
        stringParam('TAX_SERVICE_BRANCH', 'main', 'Branch for tax-service')
        stringParam('MEDIA_SERVICE_BRANCH', 'main', 'Branch for media-service')
        stringParam('SEARCH_SERVICE_BRANCH', 'main', 'Branch for search-service')
        stringParam('STOREFRONT_BFF_BRANCH', 'main', 'Branch for storefront-bff')
        stringParam('STOREFRONT_UI_BRANCH', 'main', 'Branch for storefront-ui')
        stringParam('BACKOFFICE_BFF_BRANCH', 'main', 'Branch for backoffice-bff')
        stringParam('BACKOFFICE_UI_BRANCH', 'main', 'Branch for backoffice-ui')
        stringParam('SAMPLEDATA_BRANCH', 'main', 'Branch for sampledata')
        booleanParam('DRY_RUN', true, 'Show diff without pushing GitOps commit')
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
            scriptPath('jenkins/Jenkinsfile.developer_build')
            lightweight(true)
        }
    }
}

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
