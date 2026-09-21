<#
.SYNOPSIS
    Aplica as divergencias do Windows sobre os bundles gerados por
    tools/convert-strings.ps1.

.DESCRIPTION
    Os textos que NAO sao identicos aos do app Android ficam em
    `tools/i18n-overrides/`:

      * `_parity.properties`  -> chaves que só existem no bundle pt-BR do Android
                                 (nomes de idiomas, dados de contato). Aplicado a
                                 todos os bundles, para que a paridade de chaves
                                 seja completa em qualquer idioma;
      * `<bundle>.properties` -> substituicoes especificas daquele bundle
                                 (ex.: `strings.properties` = pt-BR do Windows,
                                 `strings_en.properties` = ingles do Windows).

    O script e idempotente: rodar duas vezes produz o mesmo arquivo.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/apply-i18n-overrides.ps1
#>
[CmdletBinding()]
param(
    # Pasta dos bundles (o padrao e `src/main/resources/i18n` deste projeto).
    [string] $Destination
)

$ErrorActionPreference = 'Stop'

if (-not $Destination) {
    $Destination = Join-Path $PSScriptRoot '..\src\main\resources\i18n'
}

$overridesDirectory = Join-Path $PSScriptRoot 'i18n-overrides'
$destinationFull = [System.IO.Path]::GetFullPath($Destination)

<#
Le um .properties simples (chave=valor) preservando a ORDEM das chaves: o
arquivo gerado continua na mesma ordem do `strings.xml` do Android, e as chaves
que chegarem de um override novo entram no fim.
#>
function Read-PropertyMap {
    param([Parameter(Mandatory)][string] $Path)

    $map = [ordered]@{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $map[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1)
    }
    return $map
}

if (-not (Test-Path -LiteralPath $destinationFull)) {
    throw "Pasta de bundles nao encontrada: $destinationFull. Rode tools/convert-strings.ps1 primeiro."
}

$parityFile = Join-Path $overridesDirectory '_parity.properties'
if (-not (Test-Path -LiteralPath $parityFile)) {
    throw "Arquivo de paridade nao encontrado: $parityFile"
}
$parity = Read-PropertyMap -Path $parityFile

$summary = @()
foreach ($bundle in (Get-ChildItem -Path $destinationFull -Filter 'strings*.properties' | Sort-Object Name)) {
    $map = Read-PropertyMap -Path $bundle.FullName

    # 1) Paridade: cria a chave quando falta e uniformiza o valor quando existe.
    $fromParity = 0
    foreach ($key in $parity.Keys) {
        $map[$key] = $parity[$key]
        $fromParity++
    }

    # 2) Divergencias daquele idioma.
    $fromLocale = 0
    $localeFile = Join-Path $overridesDirectory $bundle.Name
    if (Test-Path -LiteralPath $localeFile) {
        $locale = Read-PropertyMap -Path $localeFile
        foreach ($key in $locale.Keys) {
            $map[$key] = $locale[$key]
            $fromLocale++
        }
    }

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# Gerado por tools/convert-strings.ps1 e ajustado por tools/apply-i18n-overrides.ps1.")
    $lines.Add("# Nao edite a mao: rode os dois scripts de novo (tools/i18n-overrides/ guarda as divergencias).")
    foreach ($key in $map.Keys) {
        $lines.Add("$key=$($map[$key])")
    }

    [System.IO.File]::WriteAllText(
        $bundle.FullName,
        ($lines -join "`n") + "`n",
        (New-Object System.Text.UTF8Encoding($false))
    )

    $summary += [pscustomobject]@{
        Bundle      = $bundle.Name
        Chaves      = $map.Keys.Count
        Paridade    = $fromParity
        DesteIdioma = $fromLocale
    }
}

Write-Host ''
Write-Host "Bundles ajustados em $destinationFull" -ForegroundColor Cyan
$summary | Format-Table -AutoSize
