pipeline {
    agent { label 'maven-java'}
    stages { 
        stage ('Build') {
            steps {
                sh 'mvn -Dmaven.test.failure.ignore=true clean package'
                sh 'mvn cobertura:cobertura -DskipTests -Dcobertura.report.format=xml'
            }
            post {
                always {

                    junit 'target/surefire-reports/**/*.xml'

                }
                success {
                    archiveArtifacts artifacts: 'target/amazon-ecs.hpi', fingerprint: true
                    archiveArtifacts artifacts: 'target/site/cobertura/**', fingerprint: true
                    step([$class: 'CoberturaPublisher', autoUpdateHealth: false, autoUpdateStability: false, coberturaReportFile: '**/coverage.xml', failUnhealthy: false, failUnstable: false, maxNumberOfBuilds: 0, onlyStable: false, sourceEncoding: 'ASCII', zoomCoverageChart: false])
                }
            }
        }
    }
}