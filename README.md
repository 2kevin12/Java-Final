# Restaurant Kitchen Display System (KDS) & Order Pipeline
### AI-Assisted Hospitality and Restaurant Kitchen Automation System

---

## 1. Project Overview & Architecture
The **Restaurant Kitchen Display System (KDS) & Order Pipeline** is an enterprise-grade digital kitchen automation platform built with **Java 21/25**, **Spring Boot 3.3.4**, **Spring Data JPA**, **Spring Security 6**, and an interactive, responsive full-stack **Single-Page Application (SPA)** with inlined styles and JavaScript.

The repository has been consolidated from 119 scattered files into **21 clean, high-cohesion files** (an 82.3% file reduction), organizing domain models, data repositories, transfer objects, services, exception handlers, multithreading synchronization, and web presentation into cohesive modules.

---

## 2. Directory Structure (Consolidated 21-File Architecture)

```
D:\Me\Projects\java porject\
├── pom.xml                                  # Maven project configuration & dependencies
├── README.md                                # Project documentation, architecture & run guide
├── run.bat                                  # Windows one-click execution & build script
├── run.sh                                   # Linux / macOS universal startup script
├── index.html                               # Standalone single-file web app (GitHub Pages ready)
├── data\
│   └── kdsdb.mv.db                          # Pre-seeded embedded persistent H2 database
├── src\
│   ├── main\
│   │   ├── java\com\restaurant\kds\
│   │   │   ├── RestaurantKdsApplication.java # Spring Boot main, Security, Migration, Seeder
│   │   │   ├── KitchenEntities.java          # JPA domain entities & enums (Order, MenuItem, etc.)
│   │   │   ├── KitchenRepositories.java      # Spring Data JPA repositories
│   │   │   ├── KitchenDtos.java              # Request & Response Data Transfer Objects
│   │   │   ├── KitchenExceptions.java        # Exception hierarchy & @ControllerAdvice handler
│   │   │   ├── KitchenMultithreading.java    # Producer-Consumer queue, wait/notify, overload
│   │   │   ├── KitchenServices.java          # Core business services (Order, Billing, Reports)
│   │   │   └── KitchenControllers.java       # REST APIs & web routing
│   │   └── resources\
│   │       ├── application.properties        # Unified H2 default & MySQL profile config
│   │       ├── schema-mysql.sql              # MySQL DDL schema script
│   │       └── static\
│   │           └── index.html                # Embedded static web app for Spring Boot server
│   └── test\
│       ├── java\com\restaurant\kds\
│       │   ├── KitchenOrderQueueTest.java    # Producer-Consumer multithreading JUnit tests
│       │   ├── KitchenServiceSuiteTest.java  # Consolidated service test suite (20 tests)
│       │   └── OrderApiControllerTest.java   # Spring WebMvc REST controller test suite
│       └── resources\mockito-extensions\
│           └── org.mockito.plugins.MockMaker # Mockito modern JDK inline mockmaker
```

**Total Project Files:** Exactly **21 files** (well within the ideal 10–30 range).

---

## 3. Technology Stack & Key Concepts

- **Backend Runtime**: Java 21+ LTS (Tested & verified on JDK 25)
- **Framework**: Spring Boot 3.3.4
- **Security**: Spring Security 6 with Role-Based Access Control (RBAC) & BCrypt password hashing
- **Persistence**: Spring Data JPA & Hibernate 6.5.3 with optimistic concurrency control (`@Version`)
- **Database**:
  - **Embedded / Dev**: Persistent H2 database in MySQL mode (`./data/kdsdb.mv.db`)
  - **Production**: MySQL 8 (`--spring.profiles.active=mysql` using `schema-mysql.sql`)
- **Multithreading & Concurrency**:
  - Classic **Producer-Consumer** synchronization using `wait()` and `notify()`.
  - Fixed-capacity carousel queue (`KitchenOrderQueue`, capacity: 5 tickets).
  - Front-of-house order takers (`OrderTakerThread`) pause on `wait()` when the rack is full (5/5).
  - Kitchen chefs (`ChefStationThread`) cook in FIFO order and call `notify()` to wake waiting producers.
  - Emergency overload protection: throws custom `KitchenOverloadException` when backlogs exceed cooking safety thresholds.
- **Frontend Presentation**:
  - Fully consolidated, single-file interactive web application (`index.html`) with inlined CSS/JS.
  - Bootstrap 5.3 + FontAwesome 6 + Chart.js via CDN (0 local file dependencies).
  - Synthetic kitchen sound chimes via Web Audio API (0 external sound files needed).
- **Testing**: 28 automated JUnit 5 and Mockito tests passing with `BUILD SUCCESS`.

---

## 4. How to Run the Application

### Option A: Using the Windows Batch Script
```cmd
# Start the application server (auto-builds jar if needed)
run.bat

# Force a clean rebuild and run
run.bat --build

# Run all 28 automated unit & integration tests
run.bat --test

# Run with native MySQL 8 profile
run.bat --mysql
```

### Option B: Using the Linux/macOS Bash Script
```bash
chmod +x run.sh

# Run application
./run.sh

# Run with clean build
./run.sh --build

# Run test suite
./run.sh --test
```

### Option C: Using Maven Directly
```powershell
# Run the Spring Boot application
mvn spring-boot:run

# Run the multithreaded simulation runner directly from CLI
mvn exec:java "-Dexec.mainClass=com.restaurant.kds.KitchenMultithreading"

# Run all test suites
mvn test
```

---

## 5. Hosting the Standalone Frontend on GitHub Pages

The root `index.html` is 100% self-contained and ready for free hosting on GitHub Pages:

1. **Initialize Git Repository (if not already done):**
   ```bash
   git init
   git add index.html README.md
   git commit -m "Deploy Restaurant KDS & Order Pipeline single-file app"
   ```
2. **Push to GitHub:**
   ```bash
   git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPOSITORY>.git
   git branch -M main
   git push -u origin main
   ```
3. **Enable GitHub Pages:**
   - Go to your GitHub repository in your web browser.
   - Click **Settings** > **Pages** (in the left sidebar).
   - Under **Build and deployment > Source**, select **Deploy from a branch**.
   - Under **Branch**, select `main` and folder `/ (root)`.
   - Click **Save**.
4. **Access Your Live App:**
   - In 1–2 minutes, your interactive KDS will be live at:
     `https://<YOUR_USERNAME>.github.io/<YOUR_REPOSITORY>/`

---

## 6. Seeded User Accounts

The database comes pre-configured with four role-based accounts:

| Username | Password | Role | Description |
| :--- | :--- | :--- | :--- |
| `admin` | `admin123` | **ROLE_ADMIN** | Full administrative rights, menu CRUD, stations, settings |
| `cashier` | `cashier123` | **ROLE_CASHIER** | POS order builder, payment processing, invoice view |
| `kitchen` | `kitchen123` | **ROLE_KITCHEN_STAFF** | Real-time KDS interface, bump bar, station filters |
| `manager` | `manager123` | **ROLE_MANAGER** | Analytics dashboard, shift reports & CSV exports |
