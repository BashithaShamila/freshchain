// The Jenkins twin of .github/workflows/ci.yml — same stages, same scripts,
// so the two CI systems can be compared doing identical work.
//
// Expects: a Jenkins agent with docker and kubectl on PATH, a Multibranch
// Pipeline job (that is what makes `when { branch 'main' }` meaningful), and
// two credentials:
//   ghcr        - username/password: GitHub username + a write:packages PAT
//   kubeconfig  - secret file: kubeconfig for the target cluster
pipeline {
  agent any

  options {
    // Deliberately limited to options core Jenkins understands. Anything from
    // a plugin (timestamps(), say) is a parse error — not a runtime one — on
    // an install that lacks it, which is a baffling first failure.
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '25'))
  }

  environment {
    REGISTRY = 'ghcr.io/CHANGE_ME/freshchain'
    TAG      = "sha-${env.GIT_COMMIT.take(7)}"
    MODULES  = 'gateway order-service inventory-service fulfillment-service'
  }

  stages {
    stage('Backend build and tests') {
      steps {
        // scripts/gradle.sh runs Gradle in a container with the Docker socket
        // mounted, so the Testcontainers suite (real Postgres, real Kafka)
        // works on any agent that has Docker — nothing else to install.
        sh './scripts/gradle.sh cleanTest build'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
        }
      }
    }

    stage('Frontend build') {
      steps {
        sh '''
          docker run --rm -v "$PWD/frontend":/app -w /app node:22-alpine \
            sh -c "npm ci --no-audit --no-fund && npm run build"
        '''
      }
    }

    stage('Build and push images') {
      when { branch 'main' }
      steps {
        withCredentials([usernamePassword(credentialsId: 'ghcr',
                                          usernameVariable: 'REG_USER',
                                          passwordVariable: 'REG_TOKEN')]) {
          sh '''
            echo "$REG_TOKEN" | docker login ghcr.io -u "$REG_USER" --password-stdin
            for m in $MODULES; do
              docker build -f ops/Dockerfile --build-arg MODULE=$m -t "$REGISTRY/$m:$TAG" .
              docker push "$REGISTRY/$m:$TAG"
            done
            docker build -t "$REGISTRY/frontend:$TAG" frontend
            docker push "$REGISTRY/frontend:$TAG"
            docker logout ghcr.io
          '''
        }
      }
    }

    stage('Deploy to Kubernetes') {
      when { branch 'main' }
      steps {
        // The human gate — Jenkins' answer to a GitHub environment that
        // requires a reviewer. The build parks here until someone clicks.
        input message: "Deploy ${TAG} to production?"
        withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
          sh '''
            for m in $MODULES frontend; do
              kubectl -n freshchain set image "deploy/$m" "$m=$REGISTRY/$m:$TAG"
            done
            # A deploy is not done when the command exits; it is done when the
            # rollout converges on ready pods. This fails loudly if one doesn't.
            for m in $MODULES frontend; do
              kubectl -n freshchain rollout status "deploy/$m" --timeout=300s
            done
          '''
        }
      }
    }
  }

  post {
    failure {
      echo "Build ${env.BUILD_URL} failed on ${env.BRANCH_NAME ?: 'unknown branch'}"
    }
  }
}
