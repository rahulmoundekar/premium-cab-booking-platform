pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    tools {
        jdk 'jdk-21'
        maven 'maven-3.9.9'
        nodejs 'node-22'
    }
    stages {
        stage('Backend - Verify') {
            steps {
                dir('backend/cab-booking-api') {
                    sh 'mvn -B -ntp clean verify'
                }
            }
        }
        stage('Frontend - Install and Lint') {
            steps {
                sh 'corepack enable'
                dir('frontend') {
                    sh 'pnpm install --no-frozen-lockfile'
                    sh 'pnpm --filter @suabhi/customer-web lint'
                    sh 'pnpm --filter @suabhi/admin-web lint'
                    sh 'pnpm --filter @suabhi/driver-web lint'
                }
            }
        }
        stage('Frontend - Build') {
            steps {
                dir('frontend') {
                    sh 'pnpm --filter @suabhi/customer-web build'
                    sh 'pnpm --filter @suabhi/admin-web build'
                    sh 'pnpm --filter @suabhi/driver-web build'
                }
            }
        }
        stage('Filesystem Security Scan') {
            when { expression { return env.RUN_CONTAINER_SCAN == 'true' } }
            steps {
                sh 'trivy fs --exit-code 1 --severity HIGH,CRITICAL .'
            }
        }
    }
    post {
        always {
            junit allowEmptyResults: true, testResults: 'backend/cab-booking-api/target/surefire-reports/*.xml'
        }
    }
}
