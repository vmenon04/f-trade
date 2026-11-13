def CodeCoverage() {
    try {
        sh 'mvn test jacoco:report'
    } finally {
        // publish the results even when tests fail, so Jenkins shows which ones
        junit 'target/surefire-reports/*.xml'
    }
    // Coverage plugin: coverage per build and per source line, on the build page and as a trend
    recordCoverage(tools: [[parser: 'JACOCO', pattern: 'target/site/jacoco/jacoco.xml']],
                   sourceDirectories: [[path: 'src/main/java']])
}

def getSafeTag() {
    return env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9_.-]', '-')
}

def SonarQube() {
    // SonarQube plugin: code quality and security issues, with trends and a quality gate.
    // The quality gate is configured in the SonarQube server, not here. It can be set to fail the build if
    // there are any new critical issues, or if coverage drops below a threshold, etc.
    withSonarQubeEnv('SonarQube') {
        sh """
            mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
              -Dsonar.projectKey=fintech-five \
              -Dsonar.projectName=fintech-five \
              -Dsonar.sources=src/main/java \
              -Dsonar.tests=src/test/java \
              -Dsonar.java.binaries=target/classes \
              -Dsonar.java.test.binaries=target/test-classes \
              -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
        """
    }
}

def SmokeTest() {
    // starts the image and waits for /actuator/health to report UP. application.properties isn't in git, so the
    // settings are passed as environment variables. The database is a throwaway Postgres container, never the shared
    // one, because startup drops the tables and reloads the sample data. Kafka is the platform's own broker, which
    // the container reaches by joining the platform's network (docker-compose.yml on this server), but with the
    // listeners off so the smoke test doesn't consume (and act on) real trade messages.
    def safeTag = getSafeTag()
    def name = "smoke-${safeTag}-${env.BUILD_NUMBER}"
    try {
        sh "docker network create ${name}"
        sh "docker run -d --name ${name}-db --network ${name} -e POSTGRES_DB=trading -e POSTGRES_USER=trading -e POSTGRES_PASSWORD=smoke postgres:17-alpine"
        // -h 127.0.0.1: during its first start the image runs a temporary server without TCP, so this waits for the real one
        sh "for i in \$(seq 30); do docker exec ${name}-db pg_isready -h 127.0.0.1 -U trading -d trading && exit 0; sleep 2; done; exit 1"
        // created first and started below, so it is on both networks before it looks for Kafka
        sh """docker create --name ${name}-app --network ${name} \\
            -e SPRING_DATASOURCE_URL=jdbc:postgresql://${name}-db:5432/trading \\
            -e SPRING_DATASOURCE_USERNAME=trading \\
            -e SPRING_DATASOURCE_PASSWORD=smoke \\
            -e SPRING_SQL_INIT_MODE=always \\
            -e SPRING_SQL_INIT_DATA_LOCATIONS=classpath:sample-data.sql \\
            -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \\
            -e SPRING_KAFKA_LISTENER_AUTO_STARTUP=false \\
            -e MARKET_DATA_BASE_URL=http://localhost:1 \\
            -e MARKET_DATA_API_KEY=smoke-test \\
            -e MARKET_DATA_CACHE_TTL=5m \\
            -e MANAGEMENT_HEALTH_REDIS_ENABLED=false \\
            -e JWT_SHARED_SECRET=smoke-test-secret-at-least-32-characters \\
            fintech-five:${safeTag}"""
        sh "docker network connect trading-platform_default ${name}-app"
        sh "docker start ${name}-app"
        // stops as soon as the app container exits (it failed to start), rather than waiting out the timeout
        sh """for i in \$(seq 120); do
                [ "\$(docker inspect -f '{{.State.Running}}' ${name}-app)" = true ] || { echo 'App container exited during startup'; exit 1; }
                docker exec ${name}-app wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"' && exit 0
                sleep 2
            done; echo 'App did not report UP within 4 minutes'; exit 1"""
    } finally {
        // the app's log shows why it failed to start
        sh "docker logs ${name}-app || true"
        sh "docker rm -f ${name}-app ${name}-db || true"
        sh "docker network rm ${name} || true"
    }
}

def StaticAnalysis() {
    // only the analysis tools, not the whole `mvn site`. SpotBugs reads the classes compiled in Build Image.
    sh 'mvn -B checkstyle:checkstyle spotbugs:spotbugs'
    // Warnings plugin: issues on the build page, linked to the source lines, with trends.
    // "New" issues are ones that weren't in this branch's previous build; the quality gate marks the build
    // UNSTABLE (yellow), not failed, if there are any.
    recordIssues(tools: [checkStyle(pattern: 'target/checkstyle-result.xml'),
                         spotBugs(pattern: 'target/spotbugsXml.xml')],
                 qualityGates: [[threshold: 1, type: 'NEW', criticality: 'UNSTABLE']])
}

def ReportingTests() {
    // the reporting tests are Python: install requirements.txt into a virtual environment, then run pytest.
    // runs inside the python:3.14 container (see the Reporting-Tests stage); the venv lives in the workspace
    // because the container runs as the Jenkins user, which can't install packages system-wide.
    sh 'python3 -m venv .venv-reporting'
    sh '.venv-reporting/bin/pip install -q -r requirements.txt'
    try {
        sh 'cd reporting && ../.venv-reporting/bin/python -m pytest -v --junitxml=pytest-results.xml'
    } finally {
        // publish the results even when tests fail, so Jenkins shows which ones
        junit allowEmptyResults: true, testResults: 'reporting/pytest-results.xml'
    }
}

pipeline {
    agent any
    tools {
        maven 'Maven3'
    }
    options {
        // by default, jenkins keeps all build logs, so we set a limit here to avoid excessive storage usage.
        // we set it here to only keep the last 20 build logs
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    stages {
        stage('Checkout') {
            steps {
                // multibranch jobs already check out the triggering branch, here we just make it explicit.
                checkout scm
            }
        }
        stage('Build Image') {
            steps {
                // skip compiling and running them while building (tests will be run in the Code-Coverage stage)
                sh 'mvn -B clean package -Dmaven.test.skip=true'
                script {
                    // github branch names can contain characters that are not valid in docker tags.
                    def safeTag = getSafeTag()
                    // DOCKER_BUILDKIT=1: the Dockerfile's cache mount needs BuildKit (already the default from Docker 23)
                    sh "DOCKER_BUILDKIT=1 docker build -t fintech-five:${safeTag} ."
                }
            }
        }
        stage('Smoke-Test') {
            steps {
                echo "Starting Library Smoke Test"
                script {
                    SmokeTest()
                }
            }
        }
        stage('Parallel') {
            parallel {           
                stage('Code-Coverage') {
                    steps {
                        echo "Starting Library Code Coverage"
                        script {
                            CodeCoverage()
                        }
                    }
                }
                
                stage('Static-Analysis') {
                    steps {
                        echo "Starting Library Static Analysis"
                        script {
                            StaticAnalysis()
                        }
                    }
                }

                stage('Reporting-Tests') {
                    // run this stage inside the official Python image, so the agent doesn't need Python installed.
                    // reuseNode keeps it on the same machine and workspace as the other stages.
                    agent {
                        docker {
                            image 'python:3.14'
                            reuseNode true
                        }
                    }
                    steps {
                        echo "Starting Reporting (Python) Tests"
                        script {
                            ReportingTests()
                        }
                    }
                }
            }
        }
        stage('SonarQube') {
            steps {
                echo "Starting SonarQube Analysis"
                script {
                    SonarQube()
                }
            }
        }
    }
}