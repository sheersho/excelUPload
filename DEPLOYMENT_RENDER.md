# Deploy to Render.com - Step-by-Step Guide

## Overview

This guide walks you through deploying the Excel Upload microservice to **Render.com** - a free, modern cloud platform.

**Cost**: Completely FREE (forever)  
**Deployment Time**: ~5-10 minutes  
**Includes**: 
- Java/Spring Boot microservice running 24/7
- Free PostgreSQL database (5GB)
- Git-based auto-deployments
- SSL certificate (HTTPS)

---

## Prerequisites

1. **GitHub Account** (free) - https://github.com/signup
2. **Render.com Account** (free) - https://dashboard.render.com/register
3. **Your code pushed to GitHub**

---

## Step 1: Push Your Code to GitHub

### 1a. Create a GitHub Repository

1. Go to https://github.com/new
2. Repository name: `excelUPload`
3. Description: "Excel to SQL microservice"
4. Visibility: **Public** (required for free deployment on Render.com)
5. Click **Create repository**

### 1b. Push Your Local Code

In PowerShell, from your project directory:

```powershell
cd C:\Users\YV738GP\IdeaProjects\excelUPload
git init
git add .
git commit -m "Initial commit: Excel upload microservice"
git branch -M main
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/excelUPload.git
git push -u origin main
```

Replace `YOUR_GITHUB_USERNAME` with your actual GitHub username.

**Tip**: If you don't have Git installed, download from https://git-scm.com/

---

## Step 2: Create Render.com Account & Connect GitHub

1. Go to https://dashboard.render.com/register
2. Click **Continue with GitHub**
3. Authorize Render to access your GitHub repositories
4. You'll be logged in to Render dashboard

---

## Step 3: Create Web Service on Render

### 3a. Create New Web Service

1. In Render dashboard, click **New +** → **Web Service**
2. Click **Connect** next to your `excelUPload` repository
3. If you don't see it, click **Search GitHub repositories** and search for `excelUPload`
4. Click **Connect** on the repository

### 3b. Configure Deployment

Fill in the following:

| Field | Value |
|-------|-------|
| **Name** | `excel-upload-api` |
| **Environment** | `Docker` |
| **Region** | `Frankfurt` (or closest to you) |
| **Branch** | `main` |
| **Build Command** | *(leave empty - Dockerfile handles it)* |
| **Start Command** | *(leave empty - Dockerfile handles it)* |

### 3c. Set Environment Variables

Click **Add Environment Variable** and add:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | `8080` |
| `JAVA_TOOL_OPTIONS` | `-Xmx512m -Xms256m` |

### 3d. Create Render Service

- **Plan**: Free
- Click **Create Web Service**

Render will start deploying immediately. This takes ~3-5 minutes.

---

## Step 4: Create PostgreSQL Database

### 4a. Create PostgreSQL Service

1. Back in Render dashboard, click **New +** → **PostgreSQL**
2. Fill in:

| Field | Value |
|-------|-------|
| **Name** | `excel-upload-db` |
| **Database** | `excel_upload` |
| **User** | `postgres` |
| **Region** | *Same as web service* |
| **Plan** | `Free` |

3. Click **Create Database**

This takes ~2-3 minutes to initialize.

---

## Step 5: Connect Database to Web Service

### 5a. Get Database Connection String

1. Go to your PostgreSQL service (`excel-upload-db`)
2. Copy the **External Database URL** (URL starts with `postgresql://`)

### 5b. Add to Web Service Environment

1. Go to your Web Service (`excel-upload-api`)
2. Click **Environment**
3. Click **Add Environment Variable**
4. Add:

| Key | Value |
|-----|-------|
| `DATABASE_URL` | *(paste your PostgreSQL URL here)* |

4. Click **Save Changes**

The web service will automatically redeploy with the database connection.

---

## Step 6: Verify Deployment

### 6a. Check Service Status

1. Go to `excel-upload-api` service
2. Look for **Status**: Should say `Live` with green checkmark
3. Click **Logs** tab to see startup logs

### 6b. Test API Endpoint

Your service gets a public URL: `https://excel-upload-api.onrender.com`

Test it with:

```powershell
# Test the API is alive
Invoke-WebRequest "https://excel-upload-api.onrender.com/api/records?limit=1"
```

Expected response: JSON list of records (empty if no data imported yet)

### 6c. Stop Service from Sleeping

By default, Render free tier services sleep after 15 min of inactivity. To keep it awake:

**Option 1 - Use Uptime Robot (Recommended)**
- Go to https://uptimerobot.com/
- Sign up (free)
- Create monitor: `https://excel-upload-api.onrender.com/api/records?limit=1`
- Check every 5 minutes
- This keeps your service always awake

**Option 2 - Accept the Sleep**
- First request after sleep takes ~30 seconds to wake up
- Subsequent requests are fast

---

## Step 7: Upload Excel Files to Cloud

### 7a. Option 1: Upload via API (Recommended)

Save Excel file to Render's filesystem, then import via API endpoint:

```powershell
# Example: Import from uploaded storage
$response = Invoke-WebRequest -Uri "https://excel-upload-api.onrender.com/api/import?path=/app/uploads/data.xlsx" -Method POST
$response.Content | ConvertFrom-Json
```

### 7b. Option 2: Create Admin Upload Endpoint

Consider adding a file upload endpoint to your REST API (not included in current version):

```java
@PostMapping("/api/upload")
public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
    // Save to /app/uploads/
    // Return success JSON
}
```

---

## Step 8: Configure for Your Data

Edit `src/main/resources/application-prod.properties` to match your Excel columns:

```properties
# Change these to match YOUR Excel headers and desired DB columns:
mapping.columns=receiptdate:receipt_date,invoice_no:invoice_no,enquiry_reference:enquiry_reference,policy_number:policy_number,Biller Txn Status:biller_txn_status

# Unique key columns (prevent duplicates):
dedupe.columns=invoice_no,enquiry_reference,policy_number,receipt_date

# Table name in database:
db.table=employees
```

Then:

```powershell
git add src/main/resources/application-prod.properties
git commit -m "Update prod config for Excel columns"
git push
```

Render will automatically redeploy in ~3-5 minutes.

---

## Using Your Deployed Service

### API Endpoints

**Import Excel files:**
```
POST https://excel-upload-api.onrender.com/api/import?path=/path/to/file.xlsx
```

**Get available headers:**
```
GET https://excel-upload-api.onrender.com/api/headers?path=/path/to/file.xlsx
```

**Query imported records:**
```
GET https://excel-upload-api.onrender.com/api/records?limit=100
```

### From Your Frontend (JavaScript)

```javascript
async function importExcel() {
  const response = await fetch(
    'https://excel-upload-api.onrender.com/api/import',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `path=/app/uploads/data.xlsx`
    }
  );
  
  return await response.json();
}
```

### From Your Backend (PowerShell)

```powershell
$response = Invoke-WebRequest `
  -Uri "https://excel-upload-api.onrender.com/api/import?path=C:/Users/YV738GP/excelFiles" `
  -Method POST

$response.Content | ConvertFrom-Json | Format-Table
```

---

## Monitoring & Logs

### View Real-Time Logs

1. Go to `excel-upload-api` service
2. Click **Logs**
3. See deployment, startup, and request logs

### Database Logs

1. Go to `excel-upload-db` PostgreSQL service
2. Click **Logs**
3. See connection and query logs

### Set Up Alerts

1. Go to Render settings
2. Add email notification on deployment failures
3. Check Uptime Robot for downtime alerts

---

## Troubleshooting

### Issue: Service won't start

**Check for errors:**
1. Go to **Logs** tab
2. Look for error messages during startup
3. Common issues:
   - PostgreSQL connection error → verify DATABASE_URL
   - Out of memory → reduce Java heap size in `JAVA_TOOL_OPTIONS`
   - Port binding error → check PORT variable is 8080

### Issue: Database connection fails

1. Verify `DATABASE_URL` environment variable is set
2. Verify PostgreSQL service is in **Live** status
3. Try restarting web service: **Deploy** menu → **Manual Deploy** → **Deploy latest commit**

### Issue: Excel import fails

1. Check **Logs** for detailed error
2. Verify Excel file path is correct
3. Verify `mapping.columns` matches your Excel headers
4. Verify dedupe columns exist in Excel file

### Issue: Service goes to sleep

Use Uptime Robot as described in Step 6c to keep it alive

---

## Scale Up Later (Optional)

When you're ready to pay for production:

1. Go to `excel-upload-api` settings
2. Change **Plan** from Free to Starter ($7/month)
3. Scales automatically to handle more traffic

---

## Share with Others

Your deployed service URL:
```
https://excel-upload-api.onrender.com
```

Share this URL with your team/clients. They can:
- Call API endpoints directly
- Create a frontend that uses your service
- Upload Excel files and see results in real-time

---

## Next Steps

1. ✅ Push to GitHub
2. ✅ Create Render web service
3. ✅ Create Render database
4. ✅ Connect database to web service
5. ✅ Test endpoints
6. ✅ Set up Uptime Robot to keep alive
7. ✅ Share URL with team

**You're now live on the cloud! 🚀**


