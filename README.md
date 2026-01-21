# Padilla Backend

Backend API para el sistema de gestion de leads inmobiliarios Padilla.

## Stack Tecnologico

| Tecnologia | Version |
|------------|---------|
| Java | 21 |
| Spring Boot | 3.5.0 |
| Spring Web | Incluido |
| Spring Actuator | Incluido |
| Lombok | Incluido |
| Maven | Wrapper incluido |

## Requisitos Previos

- **Java JDK 21** o superior
- **Git**

Verificar instalacion:
```bash
java -version    # Debe mostrar version 21+
git --version
```

## Instalacion

### 1. Clonar repositorio

```bash
git clone https://github.com/adrianpuche12/padilla_backend.git
cd padilla_backend
```

### 2. Compilar proyecto

```bash
# Windows
.\mvnw.cmd clean compile

# Linux/Mac
./mvnw clean compile
```

## Ejecucion Local

### Iniciar aplicacion

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux/Mac
./mvnw spring-boot:run
```

La aplicacion estara disponible en: **http://localhost:8080**

### Verificar funcionamiento

```bash
curl http://localhost:8080/health
```

Respuesta esperada:
```json
{
  "status": "UP",
  "service": "padilla-backend",
  "timestamp": "2026-01-18T12:00:00.000000"
}
```

## Endpoints Disponibles

| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| GET | `/health` | Health check personalizado |
| GET | `/actuator/health` | Health check de Actuator (detallado) |
| GET | `/actuator/info` | Informacion del sistema |

## Configuracion

### application.properties

```properties
# Servidor
server.port=8080

# Actuator
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```

### Puerto personalizado

Para cambiar el puerto, modificar `src/main/resources/application.properties`:
```properties
server.port=9090
```

## Comandos Utiles

```bash
# Compilar
.\mvnw.cmd clean compile

# Ejecutar tests
.\mvnw.cmd test

# Crear JAR para produccion
.\mvnw.cmd clean package

# Ejecutar JAR
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

## Estructura del Proyecto

```
padilla_backend/
├── src/
│   ├── main/
│   │   ├── java/com/padilla/backend/
│   │   │   ├── PadillaBackendApplication.java
│   │   │   └── controller/
│   │   │       └── HealthController.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── pom.xml
├── mvnw / mvnw.cmd
└── README.md
```

## Proximos Pasos (Sprint 1)

- [ ] T-03: Integracion con Keycloak
- [ ] T-05: Conexion segura con Frontend
- [ ] T-06: Consumo de API n8n

## Contribuir

1. Crear rama desde `dev`: `git checkout -b feature/nombre`
2. Hacer cambios y commit
3. Push: `git push origin feature/nombre`
4. Crear Pull Request hacia `dev`

## Licencia

Proyecto privado - Padilla 2026
