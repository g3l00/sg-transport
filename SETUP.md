# SG Transport - Project Setup Guide

## Prerequisites

- Node.js 20+ (for Angular)
- Java 17 (for Spring Boot backend)
- Docker & Docker Compose
- Angular CLI: `npm install -g @angular/cli`

## Backend Setup

### 1. Get LTA API Key

1. Go to https://www.mytransport.sg/content/mytransport/home/developers/LTA_DataMall.html
2. Register for an account
3. Copy your API key (AccountKey)

### 2. Setup Backend

```powershell
cd backend

# Set environment variable for LTA API Key
$env:LTA_API_KEY = "YOUR_LTA_API_KEY_HERE"

# Run with Maven Wrapper
.\mvnw.cmd spring-boot:run

# Or test
.\mvnw.cmd clean test
```

Backend runs on: http://localhost:8080

Test endpoint: http://localhost:8080/api/transport/health

## Frontend Setup

### 1. Create Angular Project

```bash
cd frontend

# Create new Angular app
ng new sg-transport
cd sg-transport

# Install dependencies
npm install

# Add Angular Material for UI
ng add @angular/material

# Add Leaflet for maps
npm install leaflet @types/leaflet
```

### 2. Create Services

**src/app/services/transport.service.ts**

```typescript
import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";

@Injectable({
  providedIn: "root",
})
export class TransportService {
  private apiUrl = "http://localhost:8080/api/transport";

  constructor(private http: HttpClient) {}

  searchBusStop(name: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/search?name=${name}`);
  }

  getBusArrival(busStopCode: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/bus-arrival/${busStopCode}`);
  }

  getBusStops(): Observable<any> {
    return this.http.get(`${this.apiUrl}/bus-stops`);
  }
}
```

### 3. Create Components

```bash
ng generate component components/bus-map
ng generate component components/bus-search
ng generate component components/bus-arrival
```

### 4. Run Frontend

```bash
ng serve
```

Frontend runs on: http://localhost:4200

## Docker & Docker Compose

### Local Development

```powershell
# Make sure you're in the project root
cd c:\Users\user\VSCode\sg-transport

# Set LTA API key
$env:LTA_API_KEY = "YOUR_LTA_API_KEY"

# Build and run
docker-compose up --build

# Clean up
docker-compose down
```

Access:

- Frontend: http://localhost
- Backend API: http://localhost:8080/api/transport
- Database: localhost:5432

### Kubernetes Deployment

```powershell
# Update the secret with your LTA API key
kubectl apply -f k8s-deployment.yaml

# Check pods
kubectl get pods

# Check services
kubectl get svc

# View logs
kubectl logs -f deployment/backend
kubectl logs -f deployment/frontend
```

## File Structure

```
sg-transport/
├── backend/
│   ├── src/
│   │   ├── main/java/com/sgtransport/backend/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   └── SgTransportApplication.java
│   │   └── resources/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/
│   ├── src/
│   │   ├── app/
│   │   │   ├── components/
│   │   │   ├── services/
│   │   │   └── app.component.ts
│   │   ├── index.html
│   │   └── main.ts
│   ├── package.json
│   ├── angular.json
│   ├── Dockerfile
│   └── nginx.conf
├── docker-compose.yml
├── k8s-deployment.yaml
└── README.md
```

## LTA API Endpoints

- **Bus Stops**: GET `/BusStops`
- **Bus Arrival**: GET `/BusArrivalv2?BusStopCode=CODE`
- **Bus Routes**: GET `/BusRoutes`
- **Traffic Images**: GET `/TrafficImages`
- **Traffic Incidents**: GET `/TrafficIncidents`

## Next Steps

1. ✅ Setup backend with LTA APIs
2. ✅ Create Angular frontend
3. Add map visualization
4. Add real-time bus tracking
5. Add search functionality
6. Deploy to Azure
7. Add authentication
8. Deploy production

## Troubleshooting

### Backend can't connect to PostgreSQL

- Check if PostgreSQL is running
- Verify connection string in `application.properties`

### Frontend can't reach backend

- Check CORS configuration in `SgTransportApplication.java`
- Verify backend is running on port 8080

### LTA API returns 401

- Verify API key is correct
- Check if account is activated

### Docker build fails

- Clear Docker cache: `docker system prune -a`
- Rebuild: `docker-compose up --build`
