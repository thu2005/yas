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
