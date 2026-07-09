param(
    [string]$PermissionFile = "permission-patient-crus-r5.json",
    [string]$OutputFile = "generated-token.txt",
    [string]$Secret = "permission-secret-1234567890-abcdef",
    [string]$Subject = "permission-user",
    [string]$Issuer = "permission-test",
    [string]$Audience = "permission-engine",
    [int]$ExpiresInMinutes = 240
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)

    $base64 = [Convert]::ToBase64String($Bytes)
    return $base64.TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Escape-JsonString {
    param([string]$Value)

    if ($null -eq $Value) {
        return ""
    }

    $builder = New-Object System.Text.StringBuilder
    foreach ($char in $Value.ToCharArray()) {
        switch ($char) {
            '"' { [void]$builder.Append('\"') }
            '\' { [void]$builder.Append('\\') }
            '/' { [void]$builder.Append('\/') }
            "`b" { [void]$builder.Append('\b') }
            "`f" { [void]$builder.Append('\f') }
            "`n" { [void]$builder.Append('\n') }
            "`r" { [void]$builder.Append('\r') }
            "`t" { [void]$builder.Append('\t') }
            default {
                $code = [int][char]$char
                if ($code -lt 32) {
                    [void]$builder.AppendFormat('\u{0:x4}', $code)
                } else {
                    [void]$builder.Append($char)
                }
            }
        }
    }
    return $builder.ToString()
}

function Resolve-ScriptPath {
    param([string]$PathValue)

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }

    return [System.IO.Path]::GetFullPath((Join-Path -Path $PSScriptRoot -ChildPath $PathValue))
}

try {
    $resolvedPermissionFile = Resolve-ScriptPath $PermissionFile
    $resolvedOutputFile = if ($OutputFile) { Resolve-ScriptPath $OutputFile } else { $null }

    Write-Host "Generating permission token..."
    Write-Host "Permission file: $resolvedPermissionFile"
    if ($resolvedOutputFile) {
        Write-Host "Output file: $resolvedOutputFile"
    }

    if (-not (Test-Path -LiteralPath $resolvedPermissionFile)) {
        throw "Permission file not found: $resolvedPermissionFile"
    }

    $permissionJson = Get-Content -LiteralPath $resolvedPermissionFile -Raw
    Write-Host "Permission file loaded."
    $now = [DateTimeOffset]::UtcNow

    Write-Host "JWT payload prepared."

    $escapedPermissionJson = Escape-JsonString $permissionJson
    $escapedSubject = Escape-JsonString $Subject
    $escapedIssuer = Escape-JsonString $Issuer
    $escapedAudience = Escape-JsonString $Audience
    $issuedAt = [int]$now.ToUnixTimeSeconds()
    $expiresAt = [int]$now.AddMinutes($ExpiresInMinutes).ToUnixTimeSeconds()

    $headerJson = '{"alg":"HS256","typ":"JWT"}'
    $payloadJson = '{"sub":"' + $escapedSubject +
        '","iss":"' + $escapedIssuer +
        '","aud":"' + $escapedAudience +
        '","preferred_username":"' + $escapedSubject +
        '","iat":' + $issuedAt +
        ',"exp":' + $expiresAt +
        ',"permissions":["' + $escapedPermissionJson + '"]}'
    Write-Host "JWT JSON serialized."

    $headerEncoded = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($headerJson))
    $payloadEncoded = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsignedToken = "$headerEncoded.$payloadEncoded"
    Write-Host "JWT header and payload encoded."

    $hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $signatureBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($unsignedToken))
    } finally {
        $hmac.Dispose()
    }
    Write-Host "JWT signature computed."

    $signatureEncoded = ConvertTo-Base64Url $signatureBytes
    $token = "$unsignedToken.$signatureEncoded"
    Write-Host "JWT token assembled."

    if ($resolvedOutputFile) {
        $outputDirectory = Split-Path -Path $resolvedOutputFile -Parent
        if ($outputDirectory) {
            New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
        }
        [System.IO.File]::WriteAllText($resolvedOutputFile, $token, [System.Text.Encoding]::UTF8)
        Write-Host "Token file written."
    }

    Write-Host "TOKEN: $token"

    if ($resolvedOutputFile) {
        Write-Host "Token written to: $resolvedOutputFile"
    }

    Write-Output $token
} catch {
    Write-Error $_
    exit 1
}
