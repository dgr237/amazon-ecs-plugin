pipeline {
    agent { label 'maven-java'}
    stages { 
        stage ('Build') {
            steps {
                sh 'mvn -Dmaven.test.failure.ignore=true clean package'
            }
            post {
                always {

                    junit 'target/surefire-reports/**/*.xml'

                }
                success {
                    archiveArtifacts artifacts: 'target/amazon-ecs.hpi', fingerprint: true
                }
            }
        }
    }
}