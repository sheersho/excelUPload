# Render.com QuickStart (5 Minutes)

## What You'll Get
✅ Live microservice at `https://your-service.onrender.com`  
✅ Free PostgreSQL database  
✅ Automatic deployments from GitHub  
✅ HTTPS (SSL included)  
✅ $0/month cost forever (free tier)

---

## Quick Steps

### 1. Push to GitHub (2 min)

```powershell
cd C:\Users\YV738GP\IdeaProjects\excelUPload
git init
git add .
git commit -m "Excel upload service"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/excelUPload.git
git push -u origin main
```

### 2. Create Render Account (1 min)

Go to https://dashboard.render.com/register  
Click **Continue with GitHub**  
✅ Authorize Render

### 3. Deploy Web Service (1 min)

1. Click **New +** → **Web Service**
2. Select your `excelUPload` repo
3. Set:
   - **Name**: `excel-upload-api`
   - **Environment**: Docker
   - **Plan**: Free
4. Click **Create Web Service**
5. Wait 3-5 minutes for deployment ✅

### 4. Create Database (1 min)

1. Click **New +** → **PostgreSQL**
2. Set:
   - **Name**: `excel-upload-db`
   - **Database**: `excel_upload`
   - **Plan**: Free
3. Click **Create Database**
4. Wait 2-3 minutes ✅

### 5. Connect Database (1 min)

1. Go to `excel-upload-db` → Copy **External Database URL**
2. Go to `excel-upload-api` → **Environment**
3. Click **Add Environment Variable**
4. Add: `DATABASE_URL` = *(paste the URL)*
5. Click **Save** → Auto-redeploys ✅

---

## Test It Works

Click **Logs** on `excel-upload-api` and wait for **Status: Live**

Then test:
```powershell
Invoke-WebRequest "https://excel-upload-api-xxxxx.onrender.com/api/records?limit=1"
```

---

## Your Live URL

```
https://excel-upload-api.onrender.com
```

Use this in your frontend/apps!

---

## Keep It Alive (Important!)

Free Render services sleep after 15 min inactivity. To keep always-on:

**Option 1: Uptime Robot (Recommended)**
- Go to https://uptimerobot.com
- Create free monitor pointing to your API
- Checks every 5 min → keeps service awake

**Option 2: Accept Sleep**
- First request = 30 sec startup
- After that = instant

---

## Full Docs

See `DEPLOYMENT_RENDER.md` for detailed instructions, troubleshooting, and advanced setup.


