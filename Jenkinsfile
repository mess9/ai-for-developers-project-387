pipeline {
    agent any

    options {
        ansiColor('xterm')
    }

    environment {
        IMAGE_NAME  = "localhost:5000/mess9/booking"
        IMAGE_TAG   = "${env.BUILD_NUMBER}"
        COMPOSE_DIR = "/home/mess9/services/docker/booking"
        APP_HOST    = "booking.fil-lost.org"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // Образ собирается тем же корневым Dockerfile, что и в e2e на GitHub:
        // multi-stage (frontend → bootJar → jre). Контекст — корень репозитория.
        stage('Build image') {
            steps {
                sh """
                    docker build \
                      -f Dockerfile \
                      -t $IMAGE_NAME:$IMAGE_TAG \
                      -t $IMAGE_NAME:latest \
                      .
                """
            }
        }

        stage('Push image') {
            when { branch 'main' }
            steps {
                sh '''
                    docker push $IMAGE_NAME:$IMAGE_TAG
                    docker push $IMAGE_NAME:latest
                '''
            }
        }

        stage('Deploy') {
            when { branch 'main' }
            steps {
                sh """
                    mkdir -p $COMPOSE_DIR
                    cp deploy/docker/compose.yaml $COMPOSE_DIR/compose.yaml

                    cd $COMPOSE_DIR
                    printf 'IMAGE_TAG=%s\\nAPP_HOST=%s\\n' "$IMAGE_TAG" "$APP_HOST" > .env

                    docker compose pull
                    docker compose up -d --force-recreate
                """
            }
        }

        stage('Smoke test') {
            when { branch 'main' }
            steps {
                sh '''
                    for i in $(seq 1 30); do
                      status=$(docker inspect -f '{{.State.Health.Status}}' booking 2>/dev/null || echo "missing")
                      echo "booking health: $status"

                      if [ "$status" = "healthy" ]; then
                        break
                      fi

                      sleep 2
                    done

                    for i in $(seq 1 30); do
                      echo "Traefik smoke attempt $i..."

                      if curl -fsS -H "Host: booking.fil-lost.org" http://traefik/actuator/health > /dev/null; then
                        echo "Smoke test passed"
                        exit 0
                      fi

                      sleep 2
                    done

                    echo "Smoke test failed"
                    curl -v -H "Host: booking.fil-lost.org" http://traefik/actuator/health || true
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            sh '''
                curl -X POST "https://n8n.fil-lost.org/webhook/jenkins-deploy" \
                  -H "Content-Type: application/json" \
                  -d "{
                    \\"project\\": \\"booking\\",
                    \\"branch\\": \\"$BRANCH_NAME\\",
                    \\"imageTag\\": \\"$IMAGE_TAG\\",
                    \\"status\\": \\"deployed\\",
                    \\"url\\": \\"https://booking.fil-lost.org\\"
                  }"
            '''
        }

        failure {
            sh '''
                curl -X POST "https://n8n.fil-lost.org/webhook/jenkins-deploy" \
                  -H "Content-Type: application/json" \
                  -d '{"project":"booking","status":"failure"}'
            '''
        }
    }
}
