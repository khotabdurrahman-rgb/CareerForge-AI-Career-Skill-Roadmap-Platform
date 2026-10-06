[CmdletBinding()]
param(
    [ValidateSet('default', 'mysql')]
    [string]$Profile = 'default'
)

$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $workspacePath 'backend'
$pomPath = Join-Path $backendPath 'pom.xml'

if (-not (Test-Path -LiteralPath $pomPath)) {
    throw "Backend not found at $pomPath. Complete the backend checkout before launching."
}

function Test-Java17([string]$JavaPath) {
    if (-not (Test-Path -LiteralPath $JavaPath)) { return $false }
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $versionText = (& $JavaPath -version 2>&1 | Out-String)
        return ($LASTEXITCODE -eq 0 -and $versionText -match 'version "17[.\"]')
    }
    finally { $ErrorActionPreference = $previousPreference }
}

$javaHomes = @()
if ($env:JAVA_HOME) { $javaHomes += $env:JAVA_HOME }
$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
if ($javaCommand) { $javaHomes += Split-Path -Parent (Split-Path -Parent $javaCommand.Source) }
$javaHomes += Join-Path $env:ProgramFiles 'Java\jdk-17'
foreach ($javaRoot in @((Join-Path $workspacePath '.tools'), (Join-Path $env:ProgramFiles 'Java'))) {
    if (Test-Path -LiteralPath $javaRoot) {
        $javaHomes += Get-ChildItem -LiteralPath $javaRoot -Directory |
            Where-Object { $_.Name -match '(jdk|java).*17' } |
            Select-Object -ExpandProperty FullName
    }
}
$selectedJavaHome = $javaHomes | Select-Object -Unique | Where-Object {
    Test-Java17 (Join-Path $_ 'bin\java.exe')
} | Select-Object -First 1
if (-not $selectedJavaHome) {
    throw 'Java 17 was not found. Install JDK 17 and set JAVA_HOME to its installation directory.'
}
$env:JAVA_HOME = $selectedJavaHome
$env:Path = "$selectedJavaHome\bin;$env:Path"

$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mavenCommand) {
    $mavenPath = $mavenCommand.Source
}
else {
    $toolsPath = Join-Path $workspacePath '.tools'
    $mavenPath = $null
    if (Test-Path -LiteralPath $toolsPath) {
        $mavenPath = Get-ChildItem -LiteralPath $toolsPath -Directory -Filter 'apache-maven-*' |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin\mvn.cmd' } |
            Where-Object { Test-Path -LiteralPath $_ } |
            Select-Object -First 1
    }
    if (-not $mavenPath) {
        throw 'Maven was not found. Install Maven on PATH or unpack it under .tools/apache-maven-*/.'
    }
}

if ($Profile -eq 'mysql') {
    foreach ($variableName in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
        if (-not [Environment]::GetEnvironmentVariable($variableName)) {
            throw "Set $variableName before launching the MySQL profile."
        }
    }
}

$arguments = @('-f', $pomPath, 'spring-boot:run', "-Dspring-boot.run.profiles=$Profile")
Write-Host "Java: $selectedJavaHome"
Write-Host "Maven: $mavenPath"
Write-Host "Starting CareerForge ($Profile) at http://localhost:8090"
Push-Location $backendPath
try {
    & $mavenPath @arguments
    $mavenExitCode = $LASTEXITCODE
}
finally { Pop-Location }
if ($mavenExitCode -ne 0) { exit $mavenExitCode }
