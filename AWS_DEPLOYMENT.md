# Deploy sg-transport to AWS App Runner

This deploys the existing single-container app from `Dockerfile.render` to AWS.

## Architecture

```text
Dockerfile.render
        |
Amazon ECR private repository
        |
AWS App Runner web service
        |
SSM Parameter Store: /sg-transport/LTA_API_KEY
```

The Render Dockerfile already builds the Angular frontend, copies it into the Spring Boot app, and runs one container. That same image works well for a first AWS deployment.

## Prerequisites

Install these on your machine:

- Docker Desktop
- AWS CLI v2

Then configure AWS credentials:

```powershell
aws configure
```

Recommended region for this project:

```text
ap-southeast-1
```

Before deploying, create an AWS budget or billing alert in the AWS console.

## Deploy

From PowerShell:

```powershell
cd C:\Users\user\VSCode\sg-transport
$env:LTA_API_KEY = "your-real-lta-datamall-key"
.\scripts\deploy-aws-apprunner.ps1
```

The script will:

1. Create an ECR repository named `sg-transport` if it does not exist.
2. Build the Docker image from `Dockerfile.render`.
3. Push the image to ECR.
4. Store `LTA_API_KEY` as an encrypted SSM parameter.
5. Create IAM roles for App Runner.
6. Create or update an App Runner service named `sg-transport`.
7. Print the public AWS URL when the service is running.

## Useful Commands

Check your AWS identity:

```powershell
aws sts get-caller-identity
```

Deploy to a different region:

```powershell
.\scripts\deploy-aws-apprunner.ps1 -Region us-east-1
```

Use a different image tag:

```powershell
.\scripts\deploy-aws-apprunner.ps1 -ImageTag v1
```

## Test

Open the URL printed by the script.

Health check:

```text
https://your-app-runner-url/api/transport/health
```

Route planner API:

```text
https://your-app-runner-url/api/transport/route-plan?from=01012&to=01014
```

## Cleanup

When you are done learning, delete the App Runner service and ECR image to avoid charges.

```powershell
aws apprunner list-services --region ap-southeast-1
```

Then delete the service from the AWS console, or with the CLI after copying the service ARN:

```powershell
aws apprunner delete-service --service-arn "your-service-arn" --region ap-southeast-1
```

Delete the ECR repository:

```powershell
aws ecr delete-repository --repository-name sg-transport --force --region ap-southeast-1
```

Delete the stored LTA key:

```powershell
aws ssm delete-parameter --name /sg-transport/LTA_API_KEY --region ap-southeast-1
```
