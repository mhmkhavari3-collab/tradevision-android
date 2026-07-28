# TradeVision - Create Keystore & Upload to GitHub Secrets
# Run this in PowerShell as Administrator

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TradeVision - Create Keystore" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ─── CONFIG ───
$keystorePath = "C:\tradevision-android\app\keystore.jks"
$alias = "tradevision"
$password = "tradevision123"
$dname = "CN=TradeVision, OU=Dev, O=TradeVision, L=Tehran, ST=Tehran, C=IR"
$repo = "mhmkhavari3-collab/tradevision-android"

# ─── STEP 1: CREATE KEYSTORE ───
Write-Host "`n[1/4] Creating keystore..." -ForegroundColor Yellow

# Find keytool (JDK must be installed)
$keytoolPath = $null
$jdkPaths = @(
    "C:\Program Files\Eclipse Adoptium\jdk-17*\bin\keytool.exe",
    "C:\Program Files\Java\jdk-17*\bin\keytool.exe",
    "C:\Program Files\Java\jdk-11*\bin\keytool.exe",
    "C:\Program Files (x86)\Java\jdk*\bin\keytool.exe"
)

foreach ($pattern in $jdkPaths) {
    $found = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) {
        $keytoolPath = $found.FullName
        break
    }
}

if (-not $keytoolPath) {
    # Try PATH
    $keytoolPath = (Get-Command keytool -ErrorAction SilentlyContinue).Source
}

if (-not $keytoolPath) {
    Write-Host "  ✗ keytool not found! Install JDK 17 first." -ForegroundColor Red
    Write-Host "  Download: https://adoptium.net/" -ForegroundColor Gray
    exit 1
}

Write-Host "  ✓ Found keytool: $keytoolPath" -ForegroundColor Green

# Create keystore directory
$keystoreDir = Split-Path -Parent $keystorePath
if (-not (Test-Path $keystoreDir)) {
    New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
}

# Generate keystore
& $keytoolPath -genkeypair -v `
    -keystore $keystorePath `
    -alias $alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -storepass $password `
    -keypass $password `
    -dname $dname

if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ Keystore generation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "  ✓ Keystore created: $keystorePath" -ForegroundColor Green

# ─── STEP 2: VERIFY KEYSTORE ───
Write-Host "`n[2/4] Verifying keystore..." -ForegroundColor Yellow
& $keytoolPath -list -keystore $keystorePath -storepass $password
Write-Host "  ✓ Keystore verified" -ForegroundColor Green

# ─── STEP 3: CONVERT TO BASE64 ───
Write-Host "`n[3/4] Converting to base64..." -ForegroundColor Yellow
$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
Write-Host "  ✓ Base64 length: $($keystoreBase64.Length) chars" -ForegroundColor Green

# Save base64 to file (backup)
$base64File = "C:\tradevision-android\keystore-base64.txt"
$keystoreBase64 | Out-File -FilePath $base64File -Encoding UTF8
Write-Host "  ✓ Saved to: $base64File" -ForegroundColor Green

# ─── STEP 4: UPLOAD TO GITHUB SECRETS ───
Write-Host "`n[4/4] Uploading to GitHub Secrets..." -ForegroundColor Yellow

# Check if gh CLI is installed
$ghInstalled = Get-Command gh -ErrorAction SilentlyContinue
if (-not $ghInstalled) {
    Write-Host "  ⚠️  gh CLI not found. Install it from:" -ForegroundColor Yellow
    Write-Host "  https://cli.github.com/" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  Manual upload steps:" -ForegroundColor Yellow
    Write-Host "  1. Go to: https://github.com/$repo/settings/secrets/actions" -ForegroundColor Gray
    Write-Host "  2. Click 'New repository secret'" -ForegroundColor Gray
    Write-Host "  3. Name: KEYSTORE_BASE64" -ForegroundColor Gray
    Write-Host "  4. Value: (copy from $base64File)" -ForegroundColor Gray
    Write-Host "  5. Repeat for:" -ForegroundColor Gray
    Write-Host "     - KEYSTORE_PASSWORD = $password" -ForegroundColor Gray
    Write-Host "     - KEY_ALIAS = $alias" -ForegroundColor Gray
    Write-Host "     - KEY_PASSWORD = $password" -ForegroundColor Gray
}
else {
    # Login check
    $ghAuth = gh auth status 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ⚠️  Not logged in to GitHub CLI" -ForegroundColor Yellow
        Write-Host "  Run: gh auth login" -ForegroundColor Gray
    }
    else {
        # Upload secrets
        Write-Host "  Uploading KEYSTORE_BASE64..."
        echo $keystoreBase64 | gh secret set KEYSTORE_BASE64 --repo $repo
        
        Write-Host "  Uploading KEYSTORE_PASSWORD..."
        echo $password | gh secret set KEYSTORE_PASSWORD --repo $repo
        
        Write-Host "  Uploading KEY_ALIAS..."
        echo $alias | gh secret set KEY_ALIAS --repo $repo
        
        Write-Host "  Uploading KEY_PASSWORD..."
        echo $password | gh secret set KEY_PASSWORD --repo $repo
        
        Write-Host "  ✓ All secrets uploaded!" -ForegroundColor Green
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  ✓ KEYSOTRE READY!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "`nKeystore Info:" -ForegroundColor Yellow
Write-Host "  File: $keystorePath" -ForegroundColor Gray
Write-Host "  Alias: $alias" -ForegroundColor Gray
Write-Host "  Password: $password" -ForegroundColor Gray
Write-Host "  Validity: 10000 days" -ForegroundColor Gray
Write-Host "`nBase64 backup saved at:" -ForegroundColor Yellow
Write-Host "  $base64File" -ForegroundColor Gray
Write-Host "`nGitHub Secrets URL:" -ForegroundColor Yellow
Write-Host "  https://github.com/$repo/settings/secrets/actions" -ForegroundColor Gray