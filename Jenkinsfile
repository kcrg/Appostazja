// Builds the API as a Native AOT linux-x64 binary and archives it as appostazja-api-linux-x64.tar.gz.
// The agent only needs git and Docker: the .NET SDK and the native toolchain come from the build image.

pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        BUILD_IMAGE = 'appostazja-api-build:11.0'
        PACKAGE = 'appostazja-api-linux-x64.tar.gz'
    }

    stages {
        stage('Build image') {
            steps {
                // Native AOT links with clang against zlib. --pull picks up SDK patch releases; when nothing
                // changed, the cached layers are reused.
                sh '''
                    printf '%s\\n' \
                        'FROM mcr.microsoft.com/dotnet/sdk:11.0' \
                        'RUN apt-get update && apt-get install -y --no-install-recommends clang zlib1g-dev && rm -rf /var/lib/apt/lists/*' \
                    | docker build --pull --tag "$BUILD_IMAGE" -
                '''
            }
        }

        stage('Test') {
            steps {
                dir('artifacts') { deleteDir() }
                inSdk '''
                    dotnet test --solution src/Appostazja.DotNet/Appostazja.slnx -c Release \
                        --results-directory artifacts/test-results -- --report-xunit-junit
                '''
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'artifacts/test-results/*.junit.xml' }
            }
        }

        stage('Publish') {
            steps {
                inSdk 'dotnet publish src/Appostazja.DotNet/Appostazja.Api -c Release -r linux-x64 -o artifacts/publish'
                // Only what the server runs: the .dbg symbols (tens of MB) stay out.
                sh '''
                    tar -C artifacts/publish -czf "artifacts/$PACKAGE" Appostazja.Api libsqlite3mc.so appsettings.json
                    cd artifacts && sha256sum "$PACKAGE" > "$PACKAGE.sha256"
                '''
                script {
                    currentBuild.description = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                }
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: "artifacts/${env.PACKAGE}, artifacts/${env.PACKAGE}.sha256", fingerprint: true
        }
    }
}

// Runs a command in the build image on the workspace, as the agent's user so the files it writes stay removable.
// NuGet packages are cached in the workspace between builds.
def inSdk(String command) {
    withEnv(["CI_COMMAND=${command}"]) {
        sh '''
            docker run --rm \
                --user "$(id -u):$(id -g)" \
                --volume "$WORKSPACE:/work" --workdir /work \
                --env HOME=/work/.ci/home \
                --env NUGET_PACKAGES=/work/.ci/nuget \
                --env DOTNET_CLI_TELEMETRY_OPTOUT=1 \
                --env DOTNET_NOLOGO=1 \
                --env CI_COMMAND \
                "$BUILD_IMAGE" bash -euo pipefail -c "$CI_COMMAND"
        '''
    }
}
