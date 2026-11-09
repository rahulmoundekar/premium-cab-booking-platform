pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds(); buildDiscarder(logRotator(numToKeepStr: '20')) }
  tools { jdk 'jdk-21'; maven 'maven-3.9.9'; nodejs 'node-22' }
  stages {
    stage('Backend - Verify') { steps { dir('backend/cab-booking-api') { sh 'mvn -B -ntp clean verify' } } }
    stage('Admin - Install, Lint, Build') {
      steps { dir('frontend/apps/admin-web') { sh 'npm ci --no-audit --no-fund'; sh 'npm run lint'; sh 'npm run build' } }
    }
    stage('Customer - Install, Build') {
      steps { dir('frontend/apps/customer-web') { sh 'npm install --no-audit --no-fund'; sh 'npm run build' } }
    }
    stage('Driver - Install, Build') {
      steps { dir('frontend/apps/driver-web') { sh 'npm install --no-audit --no-fund'; sh 'npm run build' } }
    }
  }
  post { always { junit allowEmptyResults: true, testResults: 'backend/cab-booking-api/target/surefire-reports/*.xml' } }
}
