# Deploy sg-transport to AWS EC2 with Docker

Use this path while App Runner is blocked on your AWS account.

## Architecture

```text
Dockerfile.render
        |
Amazon ECR private repository
        |
EC2 instance running Docker
        |
SSM Parameter Store: /sg-transport/LTA_API_KEY
```

The EC2 instance pulls your image from ECR, reads the LTA key from SSM Parameter Store using an instance role, and runs the container on port `80`.

## Cost Note

This creates a running EC2 instance. Stop or terminate it when you are done testing so it does not keep charging.

## Deploy

From PowerShell:

```powershell
cd C:\Users\user\VSCode\sg-transport
$env:LTA_API_KEY = "your-real-lta-datamall-key"
.\scripts\deploy-aws-ec2.ps1
```

The default region is:

```text
ap-southeast-1
```

The default instance type is:

```text
t3.micro
```

The script will:

1. Build the app image from `Dockerfile.render`.
2. Push it to ECR.
3. Store `LTA_API_KEY` in SSM Parameter Store.
4. Create an EC2 IAM role that can pull from ECR and read the SSM key.
5. Create a security group that opens HTTP port `80`.
6. Launch an Amazon Linux 2023 EC2 instance.
7. Install Docker on the instance using user data.
8. Run the container with `SPRING_PROFILES_ACTIVE=render`.

## Test

The script prints a public URL:

```text
http://your-ec2-public-dns
```

Health check:

```text
http://your-ec2-public-dns/api/transport/health
```

Route planner API:

```text
http://your-ec2-public-dns/api/transport/route-plan?from=01012&to=01014
```

The first boot can take a few minutes because EC2 has to install Docker and pull the image.

## Optional SSH

By default, SSH is not opened. To open port `22` only for your current public IP:

```powershell
.\scripts\deploy-aws-ec2.ps1 -AllowSshFromMyIp
```

The script does not create or attach an SSH key pair. This option is mainly useful if you later add a key pair manually or use EC2 Instance Connect from the AWS console.

## Useful Checks

List instances for this app:

```powershell
aws ec2 describe-instances `
  --filters "Name=tag:Name,Values=sg-transport" `
  --query "Reservations[].Instances[].{Id:InstanceId,State:State.Name,PublicDns:PublicDnsName}" `
  --region ap-southeast-1 `
  --output table
```

Check the ECR image:

```powershell
aws ecr describe-images --repository-name sg-transport --region ap-southeast-1
```

Check the SSM key exists:

```powershell
aws ssm get-parameter --name /sg-transport/LTA_API_KEY --with-decryption --region ap-southeast-1
```

## Cleanup

Terminate the EC2 instance:

```powershell
$instanceId = aws ec2 describe-instances `
  --filters "Name=tag:Name,Values=sg-transport" "Name=instance-state-name,Values=pending,running,stopping,stopped" `
  --query "Reservations[].Instances[].InstanceId | [0]" `
  --region ap-southeast-1 `
  --output text

aws ec2 terminate-instances --instance-ids $instanceId --region ap-southeast-1
```

Delete the ECR repository:

```powershell
aws ecr delete-repository --repository-name sg-transport --force --region ap-southeast-1
```

Delete the SSM key:

```powershell
aws ssm delete-parameter --name /sg-transport/LTA_API_KEY --region ap-southeast-1
```

Delete the security group after the instance is terminated:

```powershell
$sgId = aws ec2 describe-security-groups `
  --filters "Name=group-name,Values=app-sg-transport-web" `
  --query "SecurityGroups[0].GroupId" `
  --region ap-southeast-1 `
  --output text

aws ec2 delete-security-group --group-id $sgId --region ap-southeast-1
```
