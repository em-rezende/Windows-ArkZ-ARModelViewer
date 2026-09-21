<#
.SYNOPSIS
    Mostra as webcams do computador: os nomes que o Windows conhece e os índices
    que o aplicativo usa.

.DESCRIPTION
    São duas informações diferentes e as duas são necessárias:

      * o **Windows** (PnP) informa os nomes amigáveis ("Integrated Camera",
        "Logitech C920") e se o dispositivo está habilitado;
      * o **OpenCV** informa os índices que o app realmente consegue abrir — só ele
        sabe disso, porque depende do backend e do driver.

    Este script consulta o Windows e chama a tarefa `listCameras` do Gradle, que
    abre cada índice com o OpenCV. Se o número de câmeras das duas listas não
    bater, é sinal de driver/backend (normalmente resolvido usando DirectShow).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/list-cameras.ps1
#>
[CmdletBinding()]
param(
    # Índice máximo testado pelo OpenCV. Aumente quando desconfiar de uma câmera
    # que não aparece (cada índice testado custa ~1 s).
    [int] $MaxIndex = 4,

    # Não chama o Gradle (só a lista do Windows).
    [switch] $WindowsOnly
)

$ErrorActionPreference = 'Stop'

Write-Host ''
Write-Host 'Câmeras vistas pelo Windows' -ForegroundColor Cyan

$pnpCameras = @()
try {
    $pnpCameras = Get-PnpDevice -Class Camera -Status OK -ErrorAction SilentlyContinue
    if (-not $pnpCameras) {
        $pnpCameras = Get-PnpDevice -Class Image -Status OK -ErrorAction SilentlyContinue
    }
}
catch {
    Write-Warning "Não foi possível consultar os dispositivos: $($_.Exception.Message)"
}

if ($pnpCameras) {
    $pnpCameras | Select-Object Status, FriendlyName, InstanceId | Format-Table -AutoSize
}
else {
    Write-Host 'Nenhuma câmera habilitada encontrada pelo Windows.' -ForegroundColor Yellow
    Write-Host 'Verifique Configurações › Privacidade e segurança › Câmera.' -ForegroundColor DarkGray
}

if ($WindowsOnly) { return }

Write-Host 'Índices que o OpenCV abre (é o que o app usa)' -ForegroundColor Cyan
Write-Host "Testando os índices 0..$MaxIndex — cada teste abre o dispositivo, aguarde…" -ForegroundColor DarkGray

$projectRoot = Split-Path -Parent $PSScriptRoot

# O JVM escreve em UTF-8 (padrão do Java 18+) e o console do Windows usa a página
# de código OEM: sem alinhar os dois, os acentos da saída do Gradle saem trocados.
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

& (Join-Path $projectRoot 'gradlew.bat') -q listCameras "-Darkz.camera.maxIndex=$MaxIndex" --console=plain
