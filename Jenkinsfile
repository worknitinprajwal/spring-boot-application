// CloudBees CI Pipeline - Build and Push Docker Image
// ArgoCD handles deployment from Git

pipeline {
    agent any

    environment {
        // Application details
        APP_NAME = 'fitness-tracker'
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'anuddeeph2'
        IMAGE_NAME = "${DOCKER_REPO}/${APP_NAME}"

        // Use branch name + build number for image tag
        BRANCH_NAME_CLEAN = "${env.BRANCH_NAME.replaceAll('/', '-')}"
        IMAGE_TAG = "${BRANCH_NAME_CLEAN}-${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "🔄 Checking out code from branch: ${env.BRANCH_NAME}"
                checkout scm

                script {
                    env.GIT_COMMIT_SHORT = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                }
            }
        }

        stage('Build & Test') {
            steps {
                echo "🏗️ Building Spring Boot application..."
                sh 'mvn clean package -DskipTests=false'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                echo "🐳 Building Docker image..."
                script {
                    withCredentials([usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )]) {
                        sh """
                            echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin ${DOCKER_REGISTRY}

                            docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                            docker push ${IMAGE_NAME}:${IMAGE_TAG}

                            # Push branch-specific latest tag
                            docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:${BRANCH_NAME_CLEAN}-latest
                            docker push ${IMAGE_NAME}:${BRANCH_NAME_CLEAN}-latest

                            echo "✅ Image pushed: ${IMAGE_NAME}:${IMAGE_TAG}"
                        """
                    }
                }
            }
        }

        stage('Update Helm Chart') {
            steps {
                echo "📝 Updating Helm chart with new image tag..."
                script {
                    dir('k8s/helm-chart') {
                        sh """
                            sed -i.bak 's|tag: .*|tag: ${IMAGE_TAG}|' values.yaml
                            sed -i.bak 's|repository: .*|repository: ${IMAGE_NAME}|' values.yaml
                            rm -f values.yaml.bak

                            echo "Updated values.yaml:"
                            cat values.yaml | grep -A 3 'image:'
                        """
                    }

                    // Commit and push changes for ArgoCD to pick up
                    withCredentials([usernamePassword(
                        credentialsId: 'github-credentials',
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_PASS'
                    )]) {
                        sh """
                            git config user.email "ci@cloudbees.com"
                            git config user.name "CloudBees CI"

                            git add k8s/helm-chart/values.yaml
                            git commit -m "Update image tag to ${IMAGE_TAG} [skip ci]" || echo "No changes to commit"

                            # Push to current branch
                            git push https://\${GIT_USER}:\${GIT_PASS}@github.com/anuddeeph2/sample-spring-boot-app.git HEAD:${env.BRANCH_NAME} || echo "Push failed or no changes"
                        """
                    }
                }
            }
        }

        stage('Trigger ArgoCD Sync') {
            steps {
                echo "🔄 Triggering ArgoCD sync..."
                script {
                    // Determine which ArgoCD app to sync based on branch
                    def argoApp = ''
                    switch(env.BRANCH_NAME) {
                        case 'develop':
                            argoApp = 'fitness-tracker-dev'
                            break
                        case 'main':
                        case 'master':
                            argoApp = 'fitness-tracker-test'  // Change to prod if needed
                            break
                        default:
                            echo "⚠️ No ArgoCD app configured for branch ${env.BRANCH_NAME}"
                            return
                    }

                    if (argoApp) {
                        sh """
                            argocd app sync ${argoApp} --grpc-web || echo "ArgoCD sync failed"
                            argocd app wait ${argoApp} --grpc-web --timeout 300 || echo "Timeout waiting for sync"
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ Pipeline completed successfully!"
            echo "Image: ${IMAGE_NAME}:${IMAGE_TAG}"
            echo "Branch: ${env.BRANCH_NAME}"
            echo "ArgoCD will deploy automatically"
        }
        failure {
            echo "❌ Pipeline failed!"
        }
    }
}
