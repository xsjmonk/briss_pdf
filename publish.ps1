[CmdletBinding()]
param(
	[string]$Version = "2.0"
)

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$publishDirectory = Join-Path $projectRoot "Publish"
$stagingDirectory = Join-Path $projectRoot "build\publish-staging"
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
$iconPath = Join-Path $projectRoot "src\main\resources\Briss_icon_032x032.ico"
$applicationName = "briss-$Version"

function Find-JPackage {
	$candidates = @()

	$pathCommand = Get-Command "jpackage.exe" -ErrorAction SilentlyContinue
	if ($pathCommand) {
		$candidates += $pathCommand.Source
	}

	if ($env:JAVA_HOME) {
		$candidates += Join-Path $env:JAVA_HOME "bin\jpackage.exe"
	}

	$programRoots = @(
		$env:ProgramFiles,
		${env:ProgramFiles(x86)},
		(Join-Path $env:LOCALAPPDATA "Programs")
	) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

	foreach ($programRoot in $programRoots) {
		foreach ($searchPattern in @(
			(Join-Path $programRoot "*\bin\jpackage.exe"),
			(Join-Path $programRoot "*\*\bin\jpackage.exe"),
			(Join-Path $programRoot "*\*\*\bin\jpackage.exe")
		)) {
			$candidates += Get-ChildItem -Path $searchPattern -File -ErrorAction SilentlyContinue |
				Select-Object -ExpandProperty FullName
		}
	}

	foreach ($candidate in ($candidates | Select-Object -Unique)) {
		if (-not (Test-Path -LiteralPath $candidate)) {
			continue
		}

		$versionOutput = & $candidate --version 2>$null
		if ($LASTEXITCODE -eq 0) {
			return $candidate
		}
	}

	throw "jpackage was not found. Install a JDK 14 or newer and run this script again."
}

if (-not (Test-Path -LiteralPath $gradleWrapper)) {
	throw "Gradle wrapper was not found: $gradleWrapper"
}

if (-not (Test-Path -LiteralPath $iconPath)) {
	throw "Application icon was not found: $iconPath"
}

if (-not (Test-Path -LiteralPath $publishDirectory)) {
	New-Item -ItemType Directory -Path $publishDirectory | Out-Null
}

Get-ChildItem -LiteralPath $publishDirectory -Force |
	Remove-Item -Recurse -Force

if (Test-Path -LiteralPath $stagingDirectory) {
	Remove-Item -LiteralPath $stagingDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $stagingDirectory | Out-Null

try {
	Push-Location $projectRoot
	try {
		& $gradleWrapper shadowJar --no-daemon
		if ($LASTEXITCODE -ne 0) {
			throw "Gradle failed while building the dependency-inclusive JAR."
		}
	}
	finally {
		Pop-Location
	}

	$shadowJar = Get-ChildItem -LiteralPath (Join-Path $projectRoot "build\libs") -Filter "*-all.jar" -File |
		Sort-Object LastWriteTime -Descending |
		Select-Object -First 1
	if (-not $shadowJar) {
		throw "The dependency-inclusive shadow JAR was not created."
	}

	Copy-Item -LiteralPath $shadowJar.FullName -Destination (Join-Path $stagingDirectory $shadowJar.Name)

	$jpackage = Find-JPackage
	$jpackageArguments = @(
		"--type", "app-image",
		"--dest", $publishDirectory,
		"--name", $applicationName,
		"--input", $stagingDirectory,
		"--main-jar", $shadowJar.Name,
		"--main-class", "at.laborg.briss.Briss",
		"--icon", $iconPath,
		"--app-version", $Version,
		"--vendor", "BRISS",
		"--description", "BRISS PDF cropping tool",
		"--java-options", "-Xms128m",
		"--java-options", "-Xmx1024m"
	)

	& $jpackage @jpackageArguments
	if ($LASTEXITCODE -ne 0) {
		throw "jpackage failed while creating the self-contained Windows application."
	}

	$appImageDirectory = Join-Path $publishDirectory $applicationName
	$launcherPath = Join-Path $appImageDirectory "$applicationName.exe"
	if (-not (Test-Path -LiteralPath $launcherPath)) {
		throw "The packaged launcher was not created: $launcherPath"
	}

	Get-ChildItem -LiteralPath $appImageDirectory -Force |
		Move-Item -Destination $publishDirectory -Force
	Remove-Item -LiteralPath $appImageDirectory -Recurse -Force

	Write-Host ""
	Write-Host "Published self-contained application:"
	Write-Host (Join-Path $publishDirectory "$applicationName.exe")
	Write-Host "Copy the entire Publish folder to another Windows PC before running it."
}
finally {
	if (Test-Path -LiteralPath $stagingDirectory) {
		Remove-Item -LiteralPath $stagingDirectory -Recurse -Force
	}
}
