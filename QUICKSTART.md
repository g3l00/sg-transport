# SG Transport - Quick Start Guide

## 🚀 Get Started in 5 Minutes

Your LTA API Key: `OJ32rSo9R9+UK1bndPfi8A==` ✅

### Step 1: Run Backend Only (Fastest)

```powershell
cd c:\Users\user\VSCode\sg-transport\backend

# Build
docker build -t sg-transport-backend:latest .

# Run with your API key
docker run -p 8080:8080 `
  -e DB_HOST=host.docker.internal `
  -e DB_PORT=5432 `
  -e DB_NAME=sg_transport `
  -e DB_USER=postgres `
  -e DB_PASSWORD=postgres `
  -e LTA_API_KEY="OJ32rSo9R9+UK1bndPfi8A==" `
  sg-transport-backend:latest
```

**Test the API:**

```powershell
# Health check
curl http://localhost:8080/api/transport/health

# Get mock bus stops (fallback data)
curl http://localhost:8080/api/transport/bus-stops

# Get real LTA bus data
curl http://localhost:8080/api/transport/bus-arrival/01012
```

### Step 2: Run Full Stack with Docker Compose

```powershell
cd c:\Users\user\VSCode\sg-transport

# Update docker-compose.yml with your API key first, then:
docker-compose up --build

# Access:
# Frontend: http://localhost
# Backend: http://localhost:8080/api/transport
# Database: localhost:5432
```

**docker-compose.yml** (update the LTA_API_KEY):

```yaml
environment:
  LTA_API_KEY: "OJ32rSo9R9+UK1bndPfi8A=="
```

### Step 3: Run Backend Without Docker (Local Development)

```powershell
cd backend

# Make sure PostgreSQL is running
# Then:
.\mvnw.cmd spring-boot:run
```

### Step 4: Create Angular Frontend

```powershell
# In frontend directory
ng new sg-transport
cd sg-transport
npm install
npm install leaflet @angular/material @angular/cdk

# Create service
ng generate service services/transport

# Create components
ng generate component components/bus-search
ng generate component components/bus-map
ng generate component components/bus-arrival

# Run
ng serve
```

Then visit: http://localhost:4200

## 📝 API Endpoints Available

**Health Check:**

```
GET /api/transport/health
```

**Search Bus Stops:**

```
GET /api/transport/search?name=Victoria
```

**Get Bus Arrival Times:**

```
GET /api/transport/bus-arrival/01012
```

**Get All Bus Stops:**

```
GET /api/transport/bus-stops
```

## 🔧 Backend Features

✅ LTA API Integration with fallback mock data  
✅ Caching for performance  
✅ PostgreSQL persistence  
✅ REST API  
✅ CORS enabled for Angular  
✅ Logging with SLF4J  
✅ Error handling

## 📱 Frontend Features to Build

- [ ] Bus stop search
- [ ] Real-time arrival display
- [ ] Interactive map with Leaflet
- [ ] Favorites system
- [ ] Route planning
- [ ] Dark mode
- [ ] PWA support

## 🐳 Kubernetes Deployment

```powershell
# Update k8s-deployment.yaml with API key
kubectl apply -f k8s-deployment.yaml

# Check
kubectl get pods
kubectl get svc

# Access
kubectl port-forward svc/frontend-service 80:80
kubectl port-forward svc/backend-service 8080:8080
```

## 🚨 Troubleshooting

**Backend won't start:**

```powershell
# Check if port 8080 is in use
netstat -ano | findstr :8080

# Kill the process
taskkill /PID <PID> /F
```

**Docker image build fails:**

```powershell
# Clear Docker cache
docker system prune -a

# Rebuild
docker-compose up --build
```

**LTA API 401 Unauthorized:**

- Check if API key is correct
- Verify account is activated on LTA DataMall

**LTA API returns no data:**

- Wait a moment and retry (API has rate limits)
- Check mock data is working as fallback

## 📚 Next Steps

1. ✅ Backend API running → http://localhost:8080/api/transport/health
2. → Create Angular frontend and call API
3. → Add map visualization
4. → Deploy to Docker Compose
5. → Deploy to Kubernetes
6. → Deploy to Azure

## 💡 Quick Commands

```powershell
# Backend
cd backend
.\mvnw.cmd clean test              # Run tests
.\mvnw.cmd spring-boot:run         # Run locally
docker build -t sg-transport-backend:latest .
docker run -p 8080:8080 sg-transport-backend:latest

# Frontend
ng serve                            # Dev server
ng build --configuration production # Build for prod
docker build -t sg-transport-frontend:latest .

# Docker Compose
docker-compose up --build
docker-compose down
docker-compose ps

# Kubernetes
kubectl apply -f k8s-deployment.yaml
kubectl get pods -w
kubectl logs -f deployment/backend
kubectl delete -f k8s-deployment.yaml
```

Ready to go! Start with testing the backend, then we'll build the Angular frontend. 🎉
