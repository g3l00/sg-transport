param(
    [string]$Region = "ap-southeast-1",
    [string]$ServiceName = "sg-transport",
    [string]$RepositoryName = "sg-transport",
    [string]$ImageTag = "latest",
    [string]$InstanceType = "t3.micro",
    [switch]$AllowSshFromMyIp
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

function Write-Utf8NoBomFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Value, $utf8NoBom)
}

function Grant-SecurityGroupIngress {
    param(
        [string]$GroupId,
        [string]$Protocol,
        [string]$Port,
        [string]$Cidr
    )

    try {
        Invoke-Aws @(
            "ec2", "authorize-security-group-ingress",
            "--group-id", $GroupId,
            "--protocol", $Protocol,
            "--port", $Port,
            "--cidr", $Cidr,
            "--region", $Region
        ) | Out-Null
    } catch {
        if ($_.Exception.Message -notmatch "InvalidPermission.Duplicate") {
            throw
        }
    }
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
$roleName = "SgTransportEc2Role-$ServiceName"
$profileName = "SgTransportEc2Profile-$ServiceName"
$securityGroupName = "app-$ServiceName-web"
$tempDir = Join-Path $repoRoot ".aws-deploy-temp"

New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

Write-Host "AWS account: $accountId"
Write-Host "Region: $Region"
Write-Host "Image: $imageUri"
Write-Host "Instance type: $InstanceType"

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
$ecrPassword = (Invoke-Aws @("ecr", "get-login-password", "--region", $Region)) -join ""
$ecrPassword | docker login --username AWS --password-stdin $registry
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

$trustPolicyPath = Join-Path $tempDir "ec2-trust-policy.json"
$ssmPolicyPath = Join-Path $tempDir "ec2-ssm-policy.json"
$userDataPath = Join-Path $tempDir "ec2-user-data.sh"

Write-JsonFile -Path $trustPolicyPath -Depth 8 -Value @{
    Version = "2012-10-17"
    Statement = @(
        @{
            Effect = "Allow"
            Principal = @{ Service = "ec2.amazonaws.com" }
            Action = "sts:AssumeRole"
        }
    )
}

Write-JsonFile -Path $ssmPolicyPath -Depth 8 -Value @{
    Version = "2012-10-17"
    Statement = @(
        @{
            Effect = "Allow"
            Action = @(
                "ssm:GetParameter",
                "ssm:GetParameters"
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

if (-not (Test-AwsResource @("iam", "get-role", "--role-name", $roleName))) {
    Write-Host "Creating EC2 instance role..."
    Invoke-Aws @(
        "iam", "create-role",
        "--role-name", $roleName,
        "--assume-role-policy-document", "file://$trustPolicyPath"
    ) | Out-Null
}

Invoke-Aws @(
    "iam", "attach-role-policy",
    "--role-name", $roleName,
    "--policy-arn", "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
) | Out-Null

Invoke-Aws @(
    "iam", "put-role-policy",
    "--role-name", $roleName,
    "--policy-name", "$ServiceName-ssm-parameter-access",
    "--policy-document", "file://$ssmPolicyPath"
) | Out-Null

if (-not (Test-AwsResource @("iam", "get-instance-profile", "--instance-profile-name", $profileName))) {
    Write-Host "Creating EC2 instance profile..."
    Invoke-Aws @(
        "iam", "create-instance-profile",
        "--instance-profile-name", $profileName
    ) | Out-Null
}

$profile = Invoke-JsonAws @("iam", "get-instance-profile", "--instance-profile-name", $profileName)
$hasRole = @($profile.InstanceProfile.Roles | Where-Object { $_.RoleName -eq $roleName }).Count -gt 0

if (-not $hasRole) {
    Invoke-Aws @(
        "iam", "add-role-to-instance-profile",
        "--instance-profile-name", $profileName,
        "--role-name", $roleName
    ) | Out-Null
}

$vpcId = (Invoke-Aws @(
    "ec2", "describe-vpcs",
    "--filters", "Name=isDefault,Values=true",
    "--query", "Vpcs[0].VpcId",
    "--output", "text",
    "--region", $Region
)) -join ""

if (-not $vpcId -or $vpcId -eq "None") {
    throw "No default VPC found in $Region. Create a default VPC or pass a region with one."
}

$securityGroupId = (Invoke-Aws @(
    "ec2", "describe-security-groups",
    "--filters", "Name=group-name,Values=$securityGroupName", "Name=vpc-id,Values=$vpcId",
    "--query", "SecurityGroups[0].GroupId",
    "--output", "text",
    "--region", $Region
)) -join ""

if (-not $securityGroupId -or $securityGroupId -eq "None") {
    Write-Host "Creating security group $securityGroupName..."
    $securityGroup = Invoke-JsonAws @(
        "ec2", "create-security-group",
        "--group-name", $securityGroupName,
        "--description", "HTTP access for sg-transport",
        "--vpc-id", $vpcId,
        "--region", $Region
    )
    $securityGroupId = $securityGroup.GroupId
}

Write-Host "Opening HTTP port 80..."
Grant-SecurityGroupIngress -GroupId $securityGroupId -Protocol "tcp" -Port "80" -Cidr "0.0.0.0/0"

if ($AllowSshFromMyIp) {
    Write-Host "Opening SSH port 22 for your current public IP..."
    $myIp = (Invoke-RestMethod -Uri "https://checkip.amazonaws.com").Trim()
    Grant-SecurityGroupIngress -GroupId $securityGroupId -Protocol "tcp" -Port "22" -Cidr "$myIp/32"
}

$existingInstance = Invoke-JsonAws @(
    "ec2", "describe-instances",
    "--filters",
    "Name=tag:Name,Values=$ServiceName",
    "Name=instance-state-name,Values=pending,running,stopping,stopped",
    "--query", "Reservations[].Instances[] | [0]",
    "--region", $Region
)

if ($existingInstance) {
    Write-Host ""
    Write-Host "An EC2 instance for $ServiceName already exists:"
    Write-Host "Instance ID: $($existingInstance.InstanceId)"
    Write-Host "State: $($existingInstance.State.Name)"
    Write-Host "Public URL: http://$($existingInstance.PublicDnsName)"
    Write-Host ""
    Write-Host "Stop or terminate it before creating a fresh one, or update it manually by pulling $imageUri."
    exit 0
}

$amiId = (Invoke-Aws @(
    "ssm", "get-parameter",
    "--name", "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64",
    "--query", "Parameter.Value",
    "--output", "text",
    "--region", $Region
)) -join ""

$userData = @"
#!/bin/bash
set -euxo pipefail

REGION="$Region"
IMAGE_URI="$imageUri"
REGISTRY="$registry"
PARAMETER_NAME="$parameterName"
CONTAINER_NAME="$ServiceName"

dnf update -y
dnf install -y docker awscli
systemctl enable --now docker

aws ecr get-login-password --region "$Region" | docker login --username AWS --password-stdin "$registry"
LTA_API_KEY=`$(aws ssm get-parameter --name "$parameterName" --with-decryption --query 'Parameter.Value' --output text --region "$Region")

docker rm -f "$ServiceName" || true
docker pull "$imageUri"
docker run -d \
  --restart unless-stopped \
  --name "$ServiceName" \
  -p 80:10000 \
  -e SPRING_PROFILES_ACTIVE=render \
  -e LTA_API_KEY="`$LTA_API_KEY" \
  "$imageUri"
"@

Write-Utf8NoBomFile -Path $userDataPath -Value $userData

Write-Host "Waiting briefly for IAM instance profile propagation..."
Start-Sleep -Seconds 15

Write-Host "Launching EC2 instance..."
$run = Invoke-JsonAws @(
    "ec2", "run-instances",
    "--image-id", $amiId,
    "--instance-type", $InstanceType,
    "--iam-instance-profile", "Name=$profileName",
    "--security-group-ids", $securityGroupId,
    "--user-data", "file://$userDataPath",
    "--tag-specifications", "ResourceType=instance,Tags=[{Key=Name,Value=$ServiceName},{Key=Project,Value=sg-transport}]",
    "--region", $Region
)

$instanceId = $run.Instances[0].InstanceId
Write-Host "Instance ID: $instanceId"
Write-Host "Waiting for EC2 instance to run..."
Invoke-Aws @("ec2", "wait", "instance-running", "--instance-ids", $instanceId, "--region", $Region) | Out-Null

$instance = Invoke-JsonAws @(
    "ec2", "describe-instances",
    "--instance-ids", $instanceId,
    "--query", "Reservations[0].Instances[0]",
    "--region", $Region
)

$publicUrl = "http://$($instance.PublicDnsName)"
Write-Host ""
Write-Host "EC2 instance is running. User data may take a few minutes to install Docker and start the app."
Write-Host "Public URL:"
Write-Host $publicUrl
Write-Host ""
Write-Host "Health check:"
Write-Host "$publicUrl/api/transport/health"
