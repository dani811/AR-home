# 📦 AR Home Inventory - Proyecto de Inventario con Realidad Mixta y Home Assistant

> Sistema modular de código protegido, orientado a crear un inventario inteligente con guiado en realidad aumentada, integración en Home Assistant y arquitectura escalable con Angular + Spring Boot.

---

## 🗂 Índice

- [🔍 Descripción del proyecto](#-descripción-del-proyecto)
- [🚀 Tecnologías](#-tecnologías)
- [🏗 Arquitectura](#-arquitectura)
- [🧰 Instalación y uso local](#-instalación-y-uso-local)
- [📡 Integración con Home Assistant](#-integración-con-home-assistant)
- [🧪 Workflows CI/CD (GitHub Actions)](#-workflows-cicd-github-actions)
- [🧱 Estructura del repositorio](#-estructura-del-repositorio)
- [📜 Licencia](#-licencia)
- [📈 Badges](#-badges)
- [📓 Changelog](#-changelog)
- [🙋 Contribuir](#-contribuir)

---

## 🔍 Descripción del proyecto

Este proyecto permite:

- 📦 Organizar y categorizar objetos en tu casa
- 🧭 Navegar hasta ellos usando realidad aumentada
- 🏠 Integrarse como panel en Home Assistant
- 🔁 Visualización 2D/3D (plano cenital interactivo)
- 🧑‍💻 Funcionalidad multiplataforma: móvil, navegador, app

> 💡 El sistema está diseñado desde el inicio para ser modular, escalable y monetizable.

---

## 🚀 Tecnologías

| Módulo        | Tecnología principal        |
|---------------|-----------------------------|
| Frontend SPA  | Angular + Three.js + AR.js  |
| Backend API   | Java 21 + Spring Boot 3     |
| Integración   | MQTT, REST API, WS (HA)     |
| Home Assistant| Custom Panel o Add-on       |
| AR avanzada   | WebXR, ARCore, ARKit         |

> Opcional: Wi-Fi RTT, UWB, Cloud Anchors

---

## 🏗 Arquitectura

El proyecto está dividido en los siguientes módulos:

- `frontend/`: SPA Angular (web y HA)
- `backend/`: API Java + lógica + MQTT
- `ha-panel/`: Panel para Home Assistant
- `home-assistant-addon/`: Add-on empaquetado (backend + frontend)
- `.github/workflows/`: Validación de ramas

🔌 Comunicación entre frontend-backend por HTTP + MQTT + WebSocket.

📁 [docs/arquitectura.md](docs/arquitectura.md) incluye un diagrama completo.

---

## 🧰 Instalación y uso local

```bash
# Requisitos previos
- Java 21
- Node 18+ / npm
- Docker (opcional para broker MQTT y HA)

# Clona el repo
$ git clone https://github.com/tuusuario/ar-home-inventory.git

# Backend
$ cd backend && ./mvnw spring-boot:run

# Frontend
$ cd frontend && npm install && ng serve

# Ver panel
Accede a http://localhost:4200
```

📁 [docs/setup.md](docs/setup.md) contiene guía paso a paso.

---

## 📡 Integración con Home Assistant

Este proyecto se puede integrar como:

- **Panel personalizado (panel_custom)**
- **Add-on con Ingress (panel nativo embebido)**

📁 [docs/home-assistant.md](docs/home-assistant.md) incluye ejemplos de configuración YAML, rutas, y permisos requeridos.

---

## 🧪 Workflows CI/CD (GitHub Actions)

### Angular
- Verifica build
- Ejecuta tests
- Linter

### Spring Boot
- Compila
- Lanza tests
- Verifica estructura

📁 `.github/workflows/`

```yaml
angular-build.yml
spring-boot-ci.yml
```

🎯 Próximamente: Integración con SonarCloud y despliegue opcional a GitHub Pages / Docker Hub.

---

## 🧱 Estructura del repositorio

```bash
ar-home-inventory/
├── backend/
├── frontend/
├── ha-panel/
├── home-assistant-addon/
├── docs/
│   ├── arquitectura.md
│   ├── setup.md
│   ├── home-assistant.md
│   ├── contributing.md
├── .github/
│   ├── workflows/
│   │   ├── angular-build.yml
│   │   ├── spring-boot-ci.yml
│   ├── ISSUE_TEMPLATE.md
│   └── PULL_REQUEST_TEMPLATE.md
├── CHANGELOG.md
├── LICENSE
└── README.md
```

---

## 📜 Licencia

Este proyecto se publica bajo la **Business Source License 1.1 (BUSL-1.1)**:

- Puedes ver y estudiar el código
- No puedes usarlo comercialmente sin permiso explícito
- Transcurrido cierto tiempo podría relicenciarse (opcional)

📁 [`LICENSE`](LICENSE)

---

## 📈 Badges (estado del proyecto)

```md
![Angular Build](https://github.com/tuusuario/ar-home-inventory/actions/workflows/angular-build.yml/badge.svg)
![Spring Boot CI](https://github.com/tuusuario/ar-home-inventory/actions/workflows/spring-boot-ci.yml/badge.svg)
![License](https://img.shields.io/badge/license-BUSL--1.1-blue)
```

> Cuando actives SonarCloud y cobertura, se añadirán más badges.

---

## 📓 Changelog

Todos los cambios relevantes están documentados en:

📁 [`CHANGELOG.md`](CHANGELOG.md) usando [Conventional Commits](https://www.conventionalcommits.org/) y [Semantic Versioning](https://semver.org/).

---

## 🙋 Contribuir

Este repositorio sigue buenas prácticas para trabajo colaborativo:

- Estructura clara del código y módulos
- Hooks de validación (lint, build)
- Revisión por Pull Request obligatoria
- Uso de Conventional Commits

📁 [docs/contributing.md](docs/contributing.md) incluye normas de estilo, formato de commits y ciclo de vida de issues/pull requests.

