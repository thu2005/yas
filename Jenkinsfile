// HELPER FUNCTIONS

// Run a shell command and return stdout as a trimmed string
def runCapture(String cmd) {
    return sh(script: cmd, returnStdout: true).trim()
}

def readMavenModulesFromRootPom() {
    def pom = readFile('pom.xml')
    def matcher = (pom =~ /<module>([^<]+)<\/module>/)
    def modules = []
    matcher.each { m -> modules << m[1].trim() }
    return modules.unique()
}

def buildAndPushBackendImage(String service, String tag, boolean pushMainTag = true) {
    withEnv(["SERVICE_NAME=${service}", "IMAGE_TAG=${tag}"]) {
        sh '''
            docker build -t ${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:${IMAGE_TAG} ${SERVICE_NAME}
            docker push ${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:${IMAGE_TAG}
        '''

        if (pushMainTag) {
            sh '''
                docker tag ${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:${IMAGE_TAG} ${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:main
                docker push ${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:main
            '''
        }
    }
}

def retagAndPushBackendImage(String service, String sourceTag, String targetTag) {
    withEnv(["SERVICE_NAME=${service}", "SOURCE_TAG=${sourceTag}", "TARGET_TAG=${targetTag}"]) {
        sh '''
            docker pull ${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:${SOURCE_TAG}
            docker tag ${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:${SOURCE_TAG} ${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:${TARGET_TAG}
            docker push ${DOCKERHUB_NAMESPACE}/yas-${SERVICE_NAME}:${TARGET_TAG}
        '''
    }
}

def updateGitOpsTag(String environmentName, String service, String tag) {
    withEnv(["ENVIRONMENT_NAME=${environmentName}", "SERVICE_NAME=${service}", "IMAGE_TAG=${tag}"]) {
        sh '''
            sed -i -E 's#(^[[:space:]]*tag:[[:space:]]*).*$#\1"'"${IMAGE_TAG}"'"#' ${GITOPS_DIR}/environments/${ENVIRONMENT_NAME}/services/${SERVICE_NAME}.yaml
        '''
    }
}

def pushGitOpsUpdates(String gitopsBranch) {
    withEnv(["GITOPS_BRANCH=${gitopsBranch}"]) {
        sh '''
            git add environments/dev/services environments/staging/services
            if ! git diff --cached --quiet; then
                git config user.name "$GITOPS_COMMIT_USER"
                git config user.email "$GITOPS_COMMIT_EMAIL"
                git commit -m "ci: sync GitOps images"
                git push origin HEAD:${GITOPS_BRANCH}
            fi
        '''
    }
}

// Calculate the list of changed files with multi-tier fallback + PR support
def computeChangedFiles() {
    def cmd = null

    if (env.CHANGE_TARGET) {
        // Pull request: compare current branch with target branch
        cmd = "git diff --name-only origin/${env.CHANGE_TARGET}...HEAD"
    } else if (env.BRANCH_NAME && env.BRANCH_NAME != 'main' && env.BRANCH_NAME != env.CI_BASE_BRANCH) {
        // Feature branch builds should test the complete branch delta. This also
        // avoids shallow first-build diffs that make the whole repository changed.
        cmd = "git diff --name-only origin/${env.CI_BASE_BRANCH}...HEAD"
    } else if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT && env.GIT_COMMIT) {
        // Regular push: compare with last successful commit
        cmd = "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}"
    } else if (env.GIT_PREVIOUS_COMMIT && env.GIT_COMMIT) {
        // No previous successful commit available
        cmd = "git diff --name-only ${env.GIT_PREVIOUS_COMMIT}..${env.GIT_COMMIT}"
    } else {
        // Fallback: files changed in the latest commit
        cmd = 'git show --name-only --pretty="" HEAD'
    }

    try {
        def out = runCapture(cmd)
        return out.split(/\r?\n/).collect { it.trim() }.findAll { it }
    } catch (err) {
        def out = runCapture('git -c color.ui=never show --name-only --pretty="" HEAD')
        return out.split(/\r?\n/).collect { it.trim() }.findAll { it }
    }
}

//  PIPELINE

pipeline {
    agent any

    parameters {
        choice(name: 'PIPELINE_MODE', choices: ['ci'], description: 'CI mode for the root Jenkinsfile')
    }

    tools {
        jdk 'jdk21'
        maven 'maven-3'
    }

    options {
        timestamps()
        disableConcurrentBuilds()          // Prevent race conditions on shared agents
        skipDefaultCheckout(true)          // Use explicit Checkout stage for full control
        buildDiscarder(logRotator(numToKeepStr: '20'))  // Limit build history to save disk
    }

    environment {
        MVN_ARGS = '-B -ntp'   // -B: batch mode, -ntp: no transfer progress (cleaner logs)
        DOCKERHUB_NAMESPACE = 'thu2005'
        DOCKERHUB_CREDENTIALS_ID = 'dockerhub-credentials'
        GITOPS_REPO_URL = 'https://github.com/thu2005/gitops-yas.git'
        SOURCE_REPO_URL = 'https://github.com/thu2005/yas.git'
        GITOPS_DIR = 'gitops-yas'
        GITOPS_TOKEN_CREDENTIALS_ID = 'github-credentials'
        GITOPS_COMMIT_USER = 'jenkins-bot'
        GITOPS_COMMIT_EMAIL = 'jenkins@local'
        GITOPS_DEV_DIR = 'environments/dev/services'
        GITOPS_STAGING_DIR = 'environments/staging/services'
        CI_BASE_BRANCH = 'devops-cd'
    }

    stages {

        // 1. Checkout
        stage('Checkout') {
            when {
                expression { params.PIPELINE_MODE != 'cleanup' }
            }
            steps {
                script {
                    if (params.PIPELINE_MODE == 'developer_build') {
                        deleteDir()
                        git url: params.GIT_REMOTE_URL, branch: params.TARGET_BRANCH?.trim() ?: 'main'
                        sh "git fetch --no-tags origin ${params.BASE_BRANCH?.trim() ?: 'main'}"
                    } else {
                        checkout scm
                        // For PRs: fetch the target branch so git diff works correctly
                        if (env.CHANGE_TARGET) {
                            sh "git fetch --no-tags origin ${env.CHANGE_TARGET}"
                        } else if (env.BRANCH_NAME && env.BRANCH_NAME != 'main' && env.BRANCH_NAME != env.CI_BASE_BRANCH) {
                            sh "git fetch --no-tags --depth=50 origin ${env.CI_BASE_BRANCH}:refs/remotes/origin/${env.CI_BASE_BRANCH}"
                        }
                    }
                }
            }
        }

        // 2. Gitleaks Scan
        stage('Gitleaks Scan') {
            when {
                expression { params.PIPELINE_MODE == 'ci' }
            }
            steps {
                script {
                    def status = sh(
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

                    // Convert JSON report to HTML for Jenkins UI
                    sh '''
                        echo "<html><body><h2>Gitleaks Report</h2><pre>" > gitleaks-report.html
                        cat gitleaks-report.json >> gitleaks-report.html
                        echo "</pre></body></html>" >> gitleaks-report.html
                    '''

                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: '.',
                        reportFiles: 'gitleaks-report.html',
                        reportName: 'Gitleaks Report'
                    ])

                    if (status != 0) {
                        error("GITLEAKS FAILURE: secrets detected. Review the Gitleaks Report tab before merging.")
                    } else {
                        echo "No secrets detected"
                    }
                }
            }
        }

        // 3. Detect Changes
        stage('Detect Changes') {
            when {
                expression { params.PIPELINE_MODE == 'ci' }
            }
            steps {
                script {
                    def allModules = readMavenModulesFromRootPom()
                    def changedFiles = computeChangedFiles()

                    // Normalize: strip ANSI color codes, unify slashes, remove leading ./
                    def normalizedChangedFiles = changedFiles
                        .collect { it.replaceAll('\\u001B\\[[;\\d]*m', '').trim() }
                        .collect { it.replace('\\', '/') }
                        .collect { it.replaceFirst(/^\.\//, '') }
                        .findAll { it }

                    // Trigger full rebuild if root pom.xml or shared checkstyle config changed
                    def rebuildAll = normalizedChangedFiles.any { f ->
                        f.equalsIgnoreCase('pom.xml') || f.startsWith('checkstyle/')
                    }

                    // Optionally also rebuild all if Jenkinsfile itself changed
                    if (env.REBUILD_ALL_ON_JENKINSFILE?.toBoolean()) {
                        rebuildAll = rebuildAll || normalizedChangedFiles.any {
                            it.equalsIgnoreCase('Jenkinsfile')
                        }
                    }

                    // Debug: which top-level dirs were touched
                    def touchedTopDirs = normalizedChangedFiles
                        .findAll { it.contains('/') }
                        .collect { it.tokenize('/')[0] }
                        .unique()

                    // Match changed files against known Maven modules
                    def affected = allModules.findAll { module ->
                        normalizedChangedFiles.any { f ->
                            f == module || f.startsWith("${module}/")
                        }
                    }

                    if (rebuildAll) {
                        affected = allModules
                    }

                    // If common-library changed, also rebuild its downstream dependents (-amd)
                    env.MVN_MAKE_FLAGS = '-am'
                    if (affected.contains('common-library')) {
                        env.MVN_MAKE_FLAGS = '-am -amd'
                    }

                    def affectedModulesCsv = affected.join(',')
                    env.AFFECTED_MODULES = affectedModulesCsv

                    // Persist to file as a safety net for env var propagation issues
                    writeFile file: '.jenkins_affected_modules', text: affectedModulesCsv

                    // Logging
                    echo "All modules (${allModules.size()}): ${allModules.join(',')}"
                    echo "rebuildAll=${rebuildAll}"
                    echo "Touched dirs: ${touchedTopDirs.join(',')}"
                    echo "Affected modules: ${affectedModulesCsv}"
                    echo "Changed files:\n${normalizedChangedFiles.join('\n')}"

                    if (affectedModulesCsv?.trim()) {
                        currentBuild.description = "${env.BRANCH_NAME ?: ''} | modules: ${affectedModulesCsv}"
                    } else {
                        currentBuild.description = "${env.BRANCH_NAME ?: ''} | no service changes"
                        echo "No Maven module affected → build/test stages will be skipped"
                    }
                }
            }
        }

        // 4. Check Tools
        stage('Check Tools') {
            when {
                expression { params.PIPELINE_MODE == 'ci' }
            }
            steps {
                script {
                    if (!env.AFFECTED_MODULES?.trim()) {
                        echo 'No service changes detected. Skipping tool checks.'
                        return
                    }
                    sh 'which gitleaks && gitleaks version'
                    sh 'which snyk && snyk --version'
                }
            }
        }

        // 5. Build
        stage('Build') {
            when {
                expression { params.PIPELINE_MODE == 'ci' && env.AFFECTED_MODULES?.trim() }
            }
            steps {
                echo "Building affected modules: ${env.AFFECTED_MODULES}..."
                sh "mvn ${env.MVN_ARGS} -pl ${env.AFFECTED_MODULES} ${env.MVN_MAKE_FLAGS} -DskipTests clean package"
            }
        }

        // 6. Unit & Integration Tests
        stage('Unit & Integration Tests') {
            when {
                expression { params.PIPELINE_MODE == 'ci' && env.AFFECTED_MODULES?.trim() }
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
                // Collect both unit test (surefire) and integration test (failsafe) results
                junit allowEmptyResults: true,
                      testResults: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
            }
        }

        // 7. Coverage Gate
        stage('Coverage Gate') {
            when {
                expression { params.PIPELINE_MODE == 'ci' && env.AFFECTED_MODULES?.trim() }
            }
            steps {
                script {
                    def modules = env.AFFECTED_MODULES
                        .split(',')
                        .collect { it.trim() }
                        .findAll { it }

                    if (!modules || modules.isEmpty()) {
                        echo "No affected modules → skipping coverage gate"
                        return
                    }

                    echo "Running coverage gate for modules: ${modules.join(', ')}"

                    def coverageTools = modules.collect { module ->
                        [
                            parser: 'JACOCO',
                            pattern: "${module}/target/site/jacoco/jacoco.xml"
                        ]
                    }

                    recordCoverage(
                        tools: coverageTools,
                        sourceCodeRetention: 'NEVER',
                        qualityGates: [
                            [threshold: 70.0, metric: 'LINE',        baseline: 'PROJECT', criticality: 'FAILURE'],
                            [threshold: 50.0, metric: 'BRANCH',      baseline: 'PROJECT', criticality: 'FAILURE'],
                            [threshold: 70.0, metric: 'INSTRUCTION', baseline: 'PROJECT', criticality: 'FAILURE']
                        ]
                    )
                }
            }
        }

        stage('CI Docker Image Push') {
            when {
                expression {
                    params.PIPELINE_MODE == 'ci' &&
                    env.AFFECTED_MODULES?.trim() &&
                    !env.CHANGE_ID &&
                    env.BRANCH_NAME != 'main' &&
                    !env.TAG_NAME
                }
            }
            steps {
                script {
                    def services = env.AFFECTED_MODULES
                        .split(',')
                        .collect { it.trim() }
                        .findAll { it && fileExists("${it}/Dockerfile") }

                    if (!services) {
                        echo 'No deployable services detected. Skipping Docker image push.'
                        return
                    }

                    def shortCommit = runCapture('git rev-parse --short=8 HEAD')

                    withCredentials([
                        usernamePassword(credentialsId: env.DOCKERHUB_CREDENTIALS_ID, usernameVariable: 'DOCKERHUB_USERNAME', passwordVariable: 'DOCKERHUB_PASSWORD')
                    ]) {
                        sh '''
                            printf '%s' "$DOCKERHUB_PASSWORD" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
                        '''

                        services.each { service ->
                            buildAndPushBackendImage(service, shortCommit, false)
                        }

                        sh '''
                            docker logout || true
                        '''
                    }
                }
            }
        }

        // 8. SonarQube Scan
        stage('SonarQube Scan') {
            when {
                expression { params.PIPELINE_MODE == 'ci' }
            }
            steps {
                script {
                    if (!env.AFFECTED_MODULES?.trim()) {
                        echo 'No service changes detected. Skipping SonarQube scan.'
                        return
                    }

                    def affectedServices = env.AFFECTED_MODULES.split(',').findAll { it?.trim() }

                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        affectedServices.each { serviceName ->
                            def serviceDir = serviceName.trim()
                            echo "Running SonarQube scan for ${serviceDir}..."

                            dir(serviceDir) {
                                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                    timeout(time: 10, unit: 'MINUTES') {
                                        retry(2) {
                                            withEnv(["SERVICE_DIR=${serviceDir}"]) {
                                                sh '''
                                                    mvn -f ../pom.xml -pl "$SERVICE_DIR" -am sonar:sonar \
                                                      -DskipTests \
                                                      -Dsonar.token="$SONAR_TOKEN" \
                                                      -Dsonar.projectKey="hcmus-devops-project1_yas_${SERVICE_DIR}" \
                                                      -Dsonar.projectName="yas-${SERVICE_DIR}" \
                                                      -Dsonar.ws.timeout=120
                                                '''.stripIndent()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 9. Snyk Scan
        stage('Snyk Scan') {
            when {
                expression { params.PIPELINE_MODE == 'ci' }
            }
            steps {
                script {
                    if (!env.AFFECTED_MODULES?.trim()) {
                        echo 'No service changes detected. Skipping Snyk scan.'
                        return
                    }

                    def affectedServices = env.AFFECTED_MODULES.split(',').findAll { it?.trim() }

                    withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {

                        // Verify authentication before scanning
                        int snykAuthStatus = sh(
                            script: 'SNYK_TOKEN="$SNYK_TOKEN" snyk whoami >/dev/null 2>&1',
                            returnStatus: true
                        )

                        if (snykAuthStatus != 0) {
                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                error('Snyk authentication failed (401 likely). Check Jenkins credential snyk-token.')
                            }
                            return
                        }

                        affectedServices.each { serviceName ->
                            def serviceDir = serviceName.trim()
                            echo "Running Snyk scan for ${serviceDir}..."

                            dir(serviceDir) {
                                sh 'test -x ./mvnw || chmod +x ./mvnw'

                                // Dependency vulnerability scan
                                int depExitCode = sh(
                                    script: 'SNYK_TOKEN="$SNYK_TOKEN" snyk test --file=pom.xml --severity-threshold=high --skip-unresolved --prune-repeated-subdependencies',
                                    returnStatus: true
                                )

                                // Static code analysis scan (added)
                                int codeExitCode = sh(
                                    script: 'SNYK_TOKEN="$SNYK_TOKEN" snyk code test',
                                    returnStatus: true
                                )

                                if (depExitCode != 0 || codeExitCode != 0) {
                                    catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                        error("Snyk found issues in ${serviceDir}. Scan logged and stage marked UNSTABLE.")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('CD Dev GitOps Update') {
            when {
                branch 'main'
            }
            steps {
                script {
                    if (!env.AFFECTED_MODULES?.trim()) {
                        echo 'No service changes detected. Skipping dev GitOps promotion.'
                        return
                    }

                    def services = env.AFFECTED_MODULES.split(',').collect { it.trim() }.findAll { it && fileExists("${it}/Dockerfile") }
                    if (!services) {
                        echo 'No deployable services detected. Skipping dev GitOps promotion.'
                        return
                    }

                    def shortCommit = runCapture('git rev-parse --short=8 HEAD')

                    withCredentials([
                        usernamePassword(credentialsId: env.DOCKERHUB_CREDENTIALS_ID, usernameVariable: 'DOCKERHUB_USERNAME', passwordVariable: 'DOCKERHUB_PASSWORD'),
                        string(credentialsId: env.GITOPS_TOKEN_CREDENTIALS_ID, variable: 'GITOPS_TOKEN')
                    ]) {
                        sh '''
                            printf '%s' "$DOCKERHUB_PASSWORD" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
                        '''

                        services.each { service ->
                            buildAndPushBackendImage(service, shortCommit)
                        }

                        sh '''
                            rm -rf "$GITOPS_DIR"
                            git clone --depth 1 https://x-access-token:${GITOPS_TOKEN}@${GITOPS_REPO_URL#https://} "$GITOPS_DIR"
                        '''

                        dir(env.GITOPS_DIR) {
                            services.each { service ->
                                updateGitOpsTag('dev', service, shortCommit)
                            }

                            withEnv(["SHORT_COMMIT=${shortCommit}"]) {
                                sh '''
                                    git config user.name "$GITOPS_COMMIT_USER"
                                    git config user.email "$GITOPS_COMMIT_EMAIL"
                                    git add environments/dev/services
                                    if ! git diff --cached --quiet; then
                                        git commit -m "ci(dev): sync images to ${SHORT_COMMIT}"
                                        git push origin HEAD:main
                                    fi
                                '''
                            }
                        }

                        sh '''
                            docker logout || true
                        '''
                    }
                }
            }
        }

        stage('CD Staging GitOps Update') {
            when {
                tag 'v*'
            }
            steps {
                script {
                    if (!env.AFFECTED_MODULES?.trim()) {
                        echo 'No service changes detected. Skipping staging GitOps promotion.'
                        return
                    }

                    def services = env.AFFECTED_MODULES.split(',').collect { it.trim() }.findAll { it && fileExists("${it}/Dockerfile") }
                    if (!services) {
                        echo 'No deployable services detected. Skipping staging GitOps promotion.'
                        return
                    }

                    def releaseTag = env.TAG_NAME?.trim() ?: runCapture('git describe --tags --exact-match')

                    withCredentials([
                        usernamePassword(credentialsId: env.DOCKERHUB_CREDENTIALS_ID, usernameVariable: 'DOCKERHUB_USERNAME', passwordVariable: 'DOCKERHUB_PASSWORD'),
                        string(credentialsId: env.GITOPS_TOKEN_CREDENTIALS_ID, variable: 'GITOPS_TOKEN')
                    ]) {
                        sh '''
                            printf '%s' "$DOCKERHUB_PASSWORD" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
                        '''

                        services.each { service ->
                            retagAndPushBackendImage(service, 'main', releaseTag)
                        }

                        sh '''
                            rm -rf "$GITOPS_DIR"
                            git clone --depth 1 https://x-access-token:${GITOPS_TOKEN}@${GITOPS_REPO_URL#https://} "$GITOPS_DIR"
                        '''

                        dir(env.GITOPS_DIR) {
                            services.each { service ->
                                updateGitOpsTag('staging', service, releaseTag)
                            }

                            withEnv(["RELEASE_TAG=${releaseTag}"]) {
                                sh '''
                                    git config user.name "$GITOPS_COMMIT_USER"
                                    git config user.email "$GITOPS_COMMIT_EMAIL"
                                    git add environments/staging/services
                                    if ! git diff --cached --quiet; then
                                        git commit -m "ci(staging): release ${RELEASE_TAG}"
                                        git push origin HEAD:main
                                    fi
                                '''
                            }
                        }

                        sh '''
                            docker logout || true
                        '''
                    }
                }
            }
        }
    }

    // Post Actions
    post {
        always {
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/*.jar'
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/site/jacoco/**'
            archiveArtifacts allowEmptyArchive: true, artifacts: 'gitleaks-report.json'
            echo 'Pipeline finished.'
        }
        success {
            echo 'Pipeline SUCCESS'
        }
        failure {
            echo 'Pipeline FAILED'
        }
    }
}
