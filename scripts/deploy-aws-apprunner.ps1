param(
    [string]$Region = "ap-southeast-1",
    [string]$ServiceName = "sg-transport",
    [string]$RepositoryName = "sg-transport",
    [string]$ImageTag = "latest"
)

$ErrorActionPreference = "Stop"

function Assert-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is not installed or is not on PATH."
    }
}

function Format-AwsCommandForError {
    param([string[]]$Arguments)

    $safeArguments = New-Object System.Collections.Generic.List[string]
    $maskNext = $false

    foreach ($argument in $Arguments) {
        if ($maskNext) {
            $safeArguments.Add("***")
            $maskNext = $false
            continue
        }

        $safeArguments.Add($argument)

        if ($argument -in @("--value", "--password", "--secret-string")) {
            $maskNext = $true
        }
    }

    return "aws $($safeArguments -join ' ')"
}

function Invoke-Aws {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    try {
        $output = & aws @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw "AWS CLI command failed: $(Format-AwsCommandForError $Arguments)`n$($output -join [Environment]::NewLine)"
    }

    return $output
}

function Invoke-JsonAws {
    param([string[]]$Arguments)

    $output = Invoke-Aws ($Arguments + @("--output", "json"))

    if (-not $output) {
        return $null
    }

    return $output | ConvertFrom-Json
}

function Test-AwsResource {
    param([string[]]$Arguments)

    try {
        Invoke-Aws $Arguments | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Value,
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [int]$Depth = 8
    )

    $json = $Value | ConvertTo-Json -Depth $Depth
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $json, $utf8NoBom)
}

Assert-Command "aws"
Assert-Command "docker"

if (-not $env:LTA_API_KEY) {
    throw "Set `$env:LTA_API_KEY before running this script."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

$identity = Invoke-JsonAws @("sts", "get-caller-identity", "--region", $Region)
$accountId = $identity.Account
$registry = "$accountId.dkr.ecr.$Region.amazonaws.com"
$imageUri = "$registry/$RepositoryName`:$ImageTag"
$parameterName = "/$ServiceName/LTA_API_KEY"
$parameterArn = "arn:aws:ssm:$Region`:$accountId`:parameter$parameterName"
$accessRoleName = "AppRunnerECRAccessRole-$ServiceName"
$instanceRoleName = "AppRunnerInstanceRole-$ServiceName"

Write-Host "AWS account: $accountId"
Write-Host "Region: $Region"
Write-Host "Image: $imageUri"

if (-not (Test-AwsResource @("ecr", "describe-repositories", "--repository-names", $RepositoryName, "--region", $Region))) {
    Write-Host "Creating ECR repository $RepositoryName..."
    Invoke-JsonAws @(
        "ecr", "create-repository",
        "--repository-name", $RepositoryName,
        "--image-scanning-configuration", "scanOnPush=true",
        "--region", $Region
    ) | Out-Null
}

Write-Host "Logging Docker in to Amazon ECR..."
aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $registry
if ($LASTEXITCODE -ne 0) {
    throw "Docker login to ECR failed."
}

Write-Host "Building Docker image..."
docker build -f Dockerfile.render -t "$RepositoryName`:$ImageTag" .
if ($LASTEXITCODE -ne 0) {
    throw "Docker build failed."
}

Write-Host "Pushing Docker image to ECR..."
docker tag "$RepositoryName`:$ImageTag" $imageUri
docker push $imageUri
if ($LASTEXITCODE -ne 0) {
    throw "Docker push failed."
}

Write-Host "Storing LTA API key in SSM Parameter Store..."
Invoke-Aws @(
    "ssm", "put-parameter",
    "--name", $parameterName,
    "--type", "SecureString",
    "--value", $env:LTA_API_KEY,
    "--overwrite",
    "--region", $Region
) | Out-Null

$tempDir = Join-Path $repoRoot ".aws-deploy-temp"
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

$accessTrustPath = Join-Path $tempDir "apprunner-ecr-access-trust.json"
$instanceTrustPath = Join-Path $tempDir "apprunner-instance-trust.json"
$instancePolicyPath = Join-Path $tempDir "apprunner-instance-policy.json"
$sourceConfigPath = Join-Path $tempDir "apprunner-source-config.json"
$instanceConfigPath = Join-Path $tempDir "apprunner-instance-config.json"
$healthConfigPath = Join-Path $tempDir "apprunner-health-config.json"

Write-JsonFile -Path $accessTrustPath -Depth 8 -Value @{
    Version = "2012-10-17"
    Statement = @(
        @{
            Effect = "Allow"
            Principal = @{ Service = "build.apprunner.amazonaws.com" }
            Action = "sts:AssumeRole"
        }
    )
}

Write-JsonFile -Path $instanceTrustPath -Depth 8 -Value @{
    Version = "2012-10-17"
    Statement = @(
        @{
            Effect = "Allow"
            Principal = @{ Service = "tasks.apprunner.amazonaws.com" }
            Action = "sts:AssumeRole"
        }
    )
}

Write-JsonFile -Path $instancePolicyPath -Depth 8 -Value @{
    Version = "2012-10-17"
    Statement = @(
        @{
            Effect = "Allow"
            Action = @(
                "ssm:GetParameter",
                "ssm:GetParameters",
                "ssm:GetParametersByPath"
            )
            Resource = $parameterArn
        },
        @{
            Effect = "Allow"
            Action = "kms:Decrypt"
            Resource = "*"
        }
    )
}

if (-not (Test-AwsResource @("iam", "get-role", "--role-name", $accessRoleName))) {
    Write-Host "Creating App Runner ECR access role..."
    Invoke-Aws @(
        "iam", "create-role",
        "--role-name", $accessRoleName,
        "--assume-role-policy-document", "file://$accessTrustPath"
    ) | Out-Null
}

Invoke-Aws @(
    "iam", "attach-role-policy",
    "--role-name", $accessRoleName,
    "--policy-arn", "arn:aws:iam::aws:policy/service-role/AWSAppRunnerServicePolicyForECRAccess"
) | Out-Null

if (-not (Test-AwsResource @("iam", "get-role", "--role-name", $instanceRoleName))) {
    Write-Host "Creating App Runner instance role..."
    Invoke-Aws @(
        "iam", "create-role",
        "--role-name", $instanceRoleName,
        "--assume-role-policy-document", "file://$instanceTrustPath"
    ) | Out-Null
}

Invoke-Aws @(
    "iam", "put-role-policy",
    "--role-name", $instanceRoleName,
    "--policy-name", "$ServiceName-ssm-access",
    "--policy-document", "file://$instancePolicyPath"
) | Out-Null

$accessRole = Invoke-JsonAws @("iam", "get-role", "--role-name", $accessRoleName)
$instanceRole = Invoke-JsonAws @("iam", "get-role", "--role-name", $instanceRoleName)

Write-Host "Waiting briefly for IAM role propagation..."
Start-Sleep -Seconds 12

Write-JsonFile -Path $sourceConfigPath -Depth 10 -Value @{
    AuthenticationConfiguration = @{
        AccessRoleArn = $accessRole.Role.Arn
    }
    AutoDeploymentsEnabled = $true
    ImageRepository = @{
        ImageIdentifier = $imageUri
        ImageRepositoryType = "ECR"
        ImageConfiguration = @{
            Port = "10000"
            RuntimeEnvironmentVariables = @{
                SPRING_PROFILES_ACTIVE = "render"
            }
            RuntimeEnvironmentSecrets = @{
                LTA_API_KEY = $parameterArn
            }
        }
    }
}

Write-JsonFile -Path $instanceConfigPath -Depth 6 -Value @{
    InstanceRoleArn = $instanceRole.Role.Arn
}

Write-JsonFile -Path $healthConfigPath -Depth 6 -Value @{
    Protocol = "HTTP"
    Path = "/api/transport/health"
    Interval = 10
    Timeout = 5
    HealthyThreshold = 1
    UnhealthyThreshold = 5
}

$serviceArn = Invoke-Aws @(
    "apprunner", "list-services",
    "--region", $Region,
    "--query", "ServiceSummaryList[?ServiceName=='$ServiceName'].ServiceArn | [0]",
    "--output", "text"
)

if ($serviceArn -and $serviceArn -ne "None") {
    Write-Host "Updating App Runner service $ServiceName..."
    $service = Invoke-JsonAws @(
        "apprunner", "update-service",
        "--service-arn", $serviceArn,
        "--source-configuration", "file://$sourceConfigPath",
        "--instance-configuration", "file://$instanceConfigPath",
        "--health-check-configuration", "file://$healthConfigPath",
        "--region", $Region
    )
} else {
    Write-Host "Creating App Runner service $ServiceName..."
    $service = Invoke-JsonAws @(
        "apprunner", "create-service",
        "--service-name", $ServiceName,
        "--source-configuration", "file://$sourceConfigPath",
        "--instance-configuration", "file://$instanceConfigPath",
        "--health-check-configuration", "file://$healthConfigPath",
        "--region", $Region
    )
    $serviceArn = $service.Service.ServiceArn
}

Write-Host "Waiting for App Runner service to become RUNNING..."
do {
    Start-Sleep -Seconds 15
    $service = Invoke-JsonAws @(
        "apprunner", "describe-service",
        "--service-arn", $serviceArn,
        "--region", $Region
    )
    Write-Host "Status: $($service.Service.Status)"
} while ($service.Service.Status -in @("CREATE_IN_PROGRESS", "OPERATION_IN_PROGRESS"))

if ($service.Service.Status -ne "RUNNING") {
    throw "App Runner service finished with status $($service.Service.Status). Check App Runner logs in AWS."
}

Write-Host ""
Write-Host "AWS deployment is live:"
Write-Host "https://$($service.Service.ServiceUrl)"
