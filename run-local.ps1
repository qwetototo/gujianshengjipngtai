$ErrorActionPreference = 'Stop'

$javaHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHome) -or
    -not (Test-Path (Join-Path $javaHome 'bin\javac.exe')) -or
    -not (Test-Path (Join-Path $javaHome 'release'))) {
    throw '请先安装 JDK 17+ 并将 JAVA_HOME 指向 JDK 目录。'
}

$release = Get-Content (Join-Path $javaHome 'release') -Raw
if ($release -notmatch 'JAVA_VERSION="([^"]+)"') {
    throw '无法读取 JAVA_HOME 对应的 Java 版本。'
}
$javaVersion = $Matches[1]
$majorVersion = if ($javaVersion.StartsWith('1.')) { [int]$javaVersion.Split('.')[1] } else { [int]$javaVersion.Split('.')[0] }
if ($majorVersion -lt 17) {
    throw "当前 JAVA_HOME 是 Java $javaVersion；项目需要 JDK 17 或更高版本。"
}

$securePassword = $null
$passwordPointer = [IntPtr]::Zero
$previousPassword = $env:OTA_DATABASE_PASSWORD
try {
    if ([string]::IsNullOrEmpty($env:OTA_DATABASE_PASSWORD)) {
        $securePassword = Read-Host '请输入本地 MySQL 密码' -AsSecureString
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        $env:OTA_DATABASE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }
    $env:SPRING_PROFILES_ACTIVE = 'local'
    Push-Location (Join-Path $PSScriptRoot 'backend')
    try {
        & .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
} finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    if ($null -ne $securePassword) { $securePassword.Dispose() }
    $env:OTA_DATABASE_PASSWORD = $previousPassword
}
