def runCapture(String cmd) {
    return sh(script: cmd, returnStdout: true).trim()
}

def splitCsv(String value) {
    value?.split(',')?.collect { it.trim() }?.findAll { it } ?: []
}

def computeChangedFiles() {
    def cmd

    if (env.CHANGE_TARGET) {
        cmd = "git diff --name-only origin/${env.CHANGE_TARGET}...HEAD"
    } else if (env.BRANCH_NAME && env.BRANCH_NAME != 'main') {
        cmd = "git diff --name-only origin/${env.CI_BASE_BRANCH}..HEAD"
    } else if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT && env.GIT_COMMIT) {
        cmd = "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}"
    } else if (env.GIT_PREVIOUS_COMMIT && env.GIT_COMMIT) {
        cmd = "git diff --name-only ${env.GIT_PREVIOUS_COMMIT}..${env.GIT_COMMIT}"
    } else {
        cmd = 'git show --name-only --pretty="" HEAD'
    }

    try {
        def out = runCapture(cmd)
        return out.split(/\r?\n/).collect { it.trim() }.findAll { it }
    } catch (err) {
        echo "Changed-file detection failed with '${cmd}'. Falling back to latest commit only."
        def out = runCapture('git -c color.ui=never show --name-only --pretty="" HEAD')
        return out.split(/\r?\n/).collect { it.trim() }.findAll { it }
    }
}

pipeline {
    agent any

    parameters {
        booleanParam(name: 'BUILD_ALL', defaultValue: false, description: 'Build all Maven and Docker services')
        booleanParam(name: 'RUN_FEATURE_BRANCH_TESTS', defaultValue: false, description: 'Run full tests on non-main branches')
    }

    tools {
        jdk 'jdk21'
        maven 'maven-3'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        MVN_ARGS = '-B -ntp'
        CI_BASE_BRANCH = 'devops-cd'
        DOCKERHUB_NAMESPACE = 'thu2005'
        DOCKERHUB_CREDENTIALS_ID = 'dockerhub-credentials'
        MAVEN_MODULES = 'backoffice-bff cart customer inventory media order product promotion search storefront-bff tax sampledata'
        DOCKER_SERVICES = 'backoffice backoffice-bff storefront storefront-bff cart customer inventory media order product promotion search tax sampledata'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    if (env.CHANGE_TARGET) {
                        sh "git fetch --no-tags origin ${env.CHANGE_TARGET}"
                    } else if (env.BRANCH_NAME && env.BRANCH_NAME != 'main') {
                        sh "git fetch --no-tags origin ${env.CI_BASE_BRANCH}:refs/remotes/origin/${env.CI_BASE_BRANCH}"
                    }
                }
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    def allMavenModules = env.MAVEN_MODULES.split(' ') as List
                    def allDockerServices = env.DOCKER_SERVICES.split(' ') as List
                    def changedFiles = computeChangedFiles()

                    def normalized = changedFiles
                        .collect { it.replace('\\', '/').replaceFirst(/^\.\//, '').trim() }
                        .findAll { it }

                    def rebuildAll = params.BUILD_ALL || normalized.any { f ->
                        f == 'pom.xml' || f.startsWith('checkstyle/')
                    }

                    def affectedMaven = rebuildAll
                        ? allMavenModules
                        : allMavenModules.findAll { module ->
                            normalized.any { f -> f == module || f.startsWith("${module}/") }
                        }

                    def affectedDocker = rebuildAll
                        ? allDockerServices
                        : allDockerServices.findAll { service ->
                            normalized.any { f -> f == service || f.startsWith("${service}/") }
                        }

                    if (affectedMaven.contains('common-library')) {
                        affectedMaven = allMavenModules
                        affectedDocker = allDockerServices
                    }

                    env.MVN_MAKE_FLAGS = '-am'
                    env.AFFECTED_MODULES = affectedMaven.join(',')
                    env.AFFECTED_DOCKER_MODULES = affectedDocker.join(',')

                    echo "rebuildAll=${rebuildAll}"
                    echo "Affected Maven modules: ${env.AFFECTED_MODULES}"
                    echo "Affected Docker services: ${env.AFFECTED_DOCKER_MODULES}"
                    echo "Changed files:\n${normalized.join('\n')}"

                    currentBuild.description = env.AFFECTED_MODULES?.trim()
                        ? "${env.BRANCH_NAME ?: ''} | modules: ${env.AFFECTED_MODULES}"
                        : "${env.BRANCH_NAME ?: ''} | no service changes"
                }
            }
        }

        stage('Gitleaks Scan') {
            steps {
                script {
                    int status = sh(
                        script: '''
                            gitleaks detect \
                                --source . \
                                --config gitleaks.toml \
                                --report-format json \
                                --report-path gitleaks-report.json \
                                --redact
                        ''',
                        returnStatus: true
                    )

                    if (status != 0) {
                        echo 'Gitleaks found issues. Marking stage unstable so CD image build can continue for developer branches.'
                        unstable('Gitleaks found issues')
                    } else {
                        echo 'No secrets detected'
                    }
                }
            }
        }

        stage('Build') {
            when {
                expression { env.AFFECTED_MODULES?.trim() }
            }
            steps {
                sh "mvn ${env.MVN_ARGS} -pl ${env.AFFECTED_MODULES} ${env.MVN_MAKE_FLAGS} -DskipTests clean package"
            }
        }

        stage('Unit & Integration Tests') {
            when {
                expression {
                    env.AFFECTED_MODULES?.trim() &&
                    (env.BRANCH_NAME == 'main' || params.RUN_FEATURE_BRANCH_TESTS)
                }
            }
            steps {
                sh """
                    mvn ${env.MVN_ARGS} \
                        -pl ${env.AFFECTED_MODULES} ${env.MVN_MAKE_FLAGS} \
                        verify \
                        -ff \
                        -DtrimStackTrace=true \
                        -Dsurefire.printSummary=true \
                        -Dfailsafe.printSummary=true
                """
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Build and Push Docker Images') {
            when {
                expression {
                    env.AFFECTED_DOCKER_MODULES?.trim() &&
                    !env.CHANGE_ID &&
                    env.BRANCH_NAME != 'main' &&
                    !env.TAG_NAME
                }
            }
            steps {
                script {
                    def imageTag = runCapture('git rev-parse --short=8 HEAD')
                    def dockerServices = splitCsv(env.AFFECTED_DOCKER_MODULES).findAll { service ->
                        fileExists("${service}/Dockerfile")
                    }

                    if (!dockerServices) {
                        echo 'No affected service has a Dockerfile. Skipping image push.'
                        return
                    }

                    withCredentials([
                        usernamePassword(
                            credentialsId: env.DOCKERHUB_CREDENTIALS_ID,
                            usernameVariable: 'DOCKERHUB_USERNAME',
                            passwordVariable: 'DOCKERHUB_PASSWORD'
                        )
                    ]) {
                        sh '''
                            set +x
                            printf '%s' "$DOCKERHUB_PASSWORD" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
                        '''

                        dockerServices.each { service ->
                            withEnv(["SERVICE_NAME=${service}", "IMAGE_TAG=${imageTag}"]) {
                                sh '''
                                    IMAGE="${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:${IMAGE_TAG}"
                                    echo "Building ${IMAGE}"
                                    docker build --pull -t "${IMAGE}" "${SERVICE_NAME}"
                                    docker push "${IMAGE}"
                                '''
                            }
                        }

                        sh 'docker logout || true'
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts allowEmptyArchive: true,
                artifacts: '**/target/*.jar, **/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml, gitleaks-report.json'
            echo 'Pipeline finished.'
        }
        success {
            echo 'Pipeline SUCCESS'
        }
        unstable {
            echo 'Pipeline UNSTABLE'
        }
        failure {
            echo 'Pipeline FAILED'
        }
    }
}
