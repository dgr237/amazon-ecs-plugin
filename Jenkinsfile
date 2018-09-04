pipeline { 
    agent { label 'maven-java'}
    stages { 
        stage ('Build') {
            steps {
                sh 'mvn -Dmaven.test.failure.ignore=true clean package'
            }
            post {
                success {
                    junit 'target/surefire-reports/**/*.xml'
                }
            }
        }
    }
}