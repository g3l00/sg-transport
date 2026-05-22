# SG Transport Deployment

## Production Data

The backend reads live LTA DataMall data through `LTA_API_KEY`.
Do not put the key in Angular code or commit it to source control.

PowerShell:

```powershell
$env:LTA_API_KEY = "your-lta-datamall-account-key"
```

Backend local run with live LTA data and H2:

```powershell
cd C:\Users\user\VSCode\sg-transport\backend
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run
```

Frontend local run:

```powershell
cd C:\Users\user\VSCode\sg-transport\frontend\sg-transport
npm.cmd start
```

The frontend calls `/api/transport`; Angular proxies that to `localhost:8080` in development,
and Nginx proxies it to the backend container in production.

## Docker Compose

Create a local `.env` from `.env.example`, then set your real key:

```powershell
Copy-Item .env.example .env
notepad .env
```

Run:

```powershell
cd C:\Users\user\VSCode\sg-transport
docker-compose up --build
```

Open:

```text
http://localhost
```

## Free Public Hosting: Render

This repo includes a Render Blueprint in `render.yaml`.

Render deployment uses a single Docker web service:

- Angular is built first.
- The Angular production files are copied into Spring Boot static resources.
- Spring Boot serves both the app and `/api/transport`.
- The app uses an in-memory H2 database because LTA data is fetched live from DataMall.

Steps:

1. Push this repository to GitHub.
2. Go to https://dashboard.render.com.
3. Click **New +**.
4. Select **Blueprint**.
5. Connect your GitHub repository.
6. Render will detect `render.yaml`.
7. When prompted for `LTA_API_KEY`, paste your real LTA DataMall key.
8. Deploy.

Your public app URL will look like:

```text
https://sg-transport.onrender.com
```

Free Render web services can spin down when idle, so the first request after a quiet period can be slow.

## Kubernetes

Replace `LTA_API_KEY: "replace-me"` in `k8s-deployment.yaml`, or create the secret separately:

```powershell
kubectl create secret generic db-credentials `
  --from-literal=DB_USER=postgres `
  --from-literal=DB_PASSWORD=postgres `
  --from-literal=LTA_API_KEY="your-lta-datamall-account-key"
```

Then apply the manifests:

```powershell
kubectl apply -f k8s-deployment.yaml
```
