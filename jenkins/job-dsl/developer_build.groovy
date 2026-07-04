pipelineJob('developer_build') {
    description('Deploy a developer test namespace from branch-specific Docker image tags and print NodePort URLs.')
    logRotator {
        numToKeep(20)
    }

    parameters {
        stringParam('TAG_CART', 'main', 'Branch name for cart')
        stringParam('TAG_TAX', 'main', 'Branch name for tax')
        stringParam('TAG_ORDER', 'main', 'Branch name for order')
        stringParam('TAG_PRODUCT', 'main', 'Branch name for product')
        stringParam('TAG_MEDIA', 'main', 'Branch name for media')
        stringParam('TAG_CUSTOMER', 'main', 'Branch name for customer')
        stringParam('TAG_INVENTORY', 'main', 'Branch name for inventory')
        stringParam('TAG_SEARCH', 'main', 'Branch name for search')
        stringParam('TAG_SAMPLEDATA', 'main', 'Branch name for sampledata')
        stringParam('TAG_BACKOFFICE_BFF', 'main', 'Branch name for backoffice-bff')
        stringParam('TAG_STOREFRONT_BFF', 'main', 'Branch name for storefront-bff')
        stringParam('TAG_BACKOFFICE_UI', 'main', 'Branch name for backoffice-ui')
        stringParam('TAG_STOREFRONT_UI', 'main', 'Branch name for storefront-ui')
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
            scriptPath('Jenkinsfile.build')
            lightweight(true)
        }
    }
}
