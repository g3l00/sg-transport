# Singapore Transport Map

A full-stack web application for tracking buses and transport in Singapore using LTA APIs.

## Architecture

```
┌─────────────────┐
│   Angular App   │  (Port 4200)
│   (Frontend)    │
└────────┬────────┘
         │
    HTTP API
         │
┌────────▼────────┐
│  Spring Boot    │  (Port 8080)
│  (Backend)      │
└────────┬────────┘
         │
    LTA APIs
         │
┌────────▼────────┐
│  PostgreSQL     │  (Port 5432)
│  (Database)     │
└─────────────────┘
```

## Projects

- **backend/** - Spring Boot REST API
- **frontend/** - Angular web application

## Features

- [x] Real-time bus arrival times
- [x] Bus stop search
- [x] Route planning
- [x] Interactive map
- [ ] Favorites
- [ ] Notifications
- [ ] Dark mode

## Getting Started

### Backend

```bash
cd backend
./mvnw.cmd spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
ng serve
```

Visit: http://localhost:4200

## Docker & Kubernetes

```bash
# Build Docker images
docker build -t sg-transport-backend:latest ./backend
docker build -t sg-transport-frontend:latest ./frontend

# Deploy to Kubernetes
kubectl apply -f k8s-deployment.yaml
```

## LTA APIs Used

- Bus Stops
- Bus Routes
- Real-time Arrival
- Traffic Images
- Road Works

Docs: https://www.mytransport.sg/content/mytransport/home/developers/LTA_DataMall.html
