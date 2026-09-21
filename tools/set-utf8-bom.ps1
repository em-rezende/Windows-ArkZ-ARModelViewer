<#
.SYNOPSIS
    Grava um BOM UTF-8 em todos os `.ps1` desta pasta.

.DESCRIPTION
    O Windows PowerShell 5.1 le um `.ps1` SEM BOM usando a pagina de codigo ANSI
    do sistema. O efeito e duplo:

      * acentos em mensagens e comentarios saem corrompidos no console;
      * pior: um literal acentuado que o script ESCREVE em um arquivo sai
        corrompido (foi o que aconteceu com os cabecalhos dos `.properties`
        gerados por tools/convert-strings.ps1).

    Editores e o Git mantem os arquivos em UTF-8 SEM BOM, entao rode este script
    depois de editar qualquer `.ps1` de `tools/`. Ele e idempotente.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/set-utf8-bom.ps1
#>
[CmdletBinding()]
param(
    # Pasta com os scripts (o padrao e a propria pasta de tools/).
    [string] $Directory
)

$ErrorActionPreference = 'Stop'

if (-not $Directory) {
    $Directory = $PSScriptRoot
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$utf8WithBom = New-Object System.Text.UTF8Encoding($true)
$changed = 0

foreach ($file in (Get-ChildItem -Path $Directory -Filter '*.ps1' -File | Sort-Object Name)) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    if ($hasBom) {
        Write-Host "Ja tem BOM: $($file.Name)" -ForegroundColor DarkGray
        continue
    }

    $text = $utf8NoBom.GetString($bytes)
    [System.IO.File]::WriteAllText($file.FullName, $text, $utf8WithBom)
    Write-Host "BOM adicionado: $($file.Name)" -ForegroundColor Green
    $changed++
}

Write-Host ''
Write-Host "$changed arquivo(s) ajustado(s) em $Directory" -ForegroundColor Cyan
