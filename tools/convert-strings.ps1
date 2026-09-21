<#
.SYNOPSIS
    Converte os `strings.xml` do app Android (ArkZ ARModelViewer) nos bundles
    `.properties` (ResourceBundle) usados pela versão Windows.

.DESCRIPTION
    A versão Windows reaproveita os MESMOS textos do app Android, inclusive todas
    as traduções. Em vez de digitar 8 × 110 strings de novo (e introduzir erros),
    este script lê cada pasta `values*/strings.xml` e gera o bundle equivalente em
    `src/main/resources/i18n/`.

    Conversões feitas em cada valor:
      * escapes do Android (`\n`, `\t`, `\'`, `\"`, `\\`, `\uXXXX`) são decodificados;
      * entidades XML (`&amp;`, `&lt;`, ...) são decodificadas pelo próprio parser;
      * o texto é reescapado para o formato `.properties` (barra invertida, quebra
        de linha, tabulação e espaço no início);
      * a saída é UTF-8 **sem BOM** — o `PropertyResourceBundle` do Java 9+ lê
        UTF-8, mas um BOM corromperia a primeira chave do arquivo.

    Os textos que MUDAM no Windows (permissões de câmera do Android, `adb logcat`,
    Google Play, "aparelho" no lugar de "computador") NÃO ficam aqui: estão em
    `tools/i18n-overrides/<locale>.properties` e são aplicados depois por
    `tools/apply-i18n-overrides.ps1`. Assim a divergência fica explícita e
    revisável, em vez de escondida dentro do gerador.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/convert-strings.ps1

.EXAMPLE
    # A partir de um clone do projeto Android em outro caminho:
    powershell -ExecutionPolicy Bypass -File tools/convert-strings.ps1 `
        -SourceRoot "C:\dev\Android-ArkZ-ARModelViewer\app\src\main\res"
#>
[CmdletBinding()]
param(
    # Pasta `res` do projeto Android (com as subpastas values*, strings.xml).
    # Padrão: o clone de referência em %TEMP% (veja `docs/development.md`).
    [string] $SourceRoot,

    # Pasta de saída dos bundles. Padrão: `src/main/resources/i18n` deste projeto.
    [string] $Destination
)

$ErrorActionPreference = 'Stop'

# Os padrões são resolvidos AQUI (e não no bloco `param`): no Windows PowerShell
# 5.1 o `$PSScriptRoot` ainda não existe quando os valores padrão são avaliados.
if (-not $SourceRoot) {
    $SourceRoot = Join-Path $env:TEMP 'ArkZ-Android-Ref\app\src\main\res'
}
if (-not $Destination) {
    $Destination = Join-Path $PSScriptRoot '..\src\main\resources\i18n'
}

# Pasta do Android -> bundle do Windows.
#
# O bundle PADRAO (`strings.properties`) E o pt-BR, exatamente como o `values/`
# do Android (que tambem e portugues do Brasil). Por isso NAO existe um
# `strings_pt_BR.properties`: `Locale("pt","BR")` cai no bundle padrao (o
# ResourceBundle e lido com `getNoFallbackControl`, que nao usa o locale do
# sistema como fallback). As duas pastas apontam para o mesmo arquivo e a
# `values-pt-rBR` e processada DEPOIS: se um dia os textos divergirem, o pt-BR
# explicito vence. O txto aqui e ASCII de proposito: o Windows PowerShell 5.1
# le o .ps1 como ANSI quando ele nao tem BOM, e acentos sairiam corrompidos.
$languages = @(
    @{ Folder = 'values';        Bundle = 'strings.properties';      Locale = 'pt-BR (padrao)' }
    @{ Folder = 'values-pt-rBR'; Bundle = 'strings.properties';      Locale = 'pt-BR (padrao)' }
    @{ Folder = 'values-pt-rPT'; Bundle = 'strings_pt_PT.properties'; Locale = 'pt-PT' }
    @{ Folder = 'values-en';     Bundle = 'strings_en.properties';    Locale = 'en' }
    @{ Folder = 'values-es';     Bundle = 'strings_es.properties';    Locale = 'es' }
    @{ Folder = 'values-fr';     Bundle = 'strings_fr.properties';    Locale = 'fr' }
    @{ Folder = 'values-de';     Bundle = 'strings_de.properties';    Locale = 'de' }
    @{ Folder = 'values-it';     Bundle = 'strings_it.properties';    Locale = 'it' }
    @{ Folder = 'values-zh-rCN'; Bundle = 'strings_zh_CN.properties'; Locale = 'zh-CN' }
)

<#
Decodifica os escapes que o `getString()` do Android entende.
Uma sequência desconhecida é mantida como está (barra + caractere): o script não
inventa significado para o que não reconhece.
#>
function ConvertFrom-AndroidEscape {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Text)

    $builder = [System.Text.StringBuilder]::new()
    for ($i = 0; $i -lt $Text.Length; $i++) {
        $char = $Text[$i]
        if ($char -ne '\' -or $i -eq ($Text.Length - 1)) {
            [void] $builder.Append($char)
            continue
        }

        $i++
        $next = $Text[$i]
        switch -CaseSensitive ($next) {
            'n' { [void] $builder.Append("`n") }
            't' { [void] $builder.Append("`t") }
            'r' { [void] $builder.Append("`r") }
            "'" { [void] $builder.Append("'") }
            '"' { [void] $builder.Append('"') }
            '\' { [void] $builder.Append('\') }
            ' ' { [void] $builder.Append(' ') }
            'u' {
                if (($i + 4) -lt $Text.Length -and $Text.Substring($i + 1, 4) -match '^[0-9a-fA-F]{4}$') {
                    [void] $builder.Append([char] [Convert]::ToInt32($Text.Substring($i + 1, 4), 16))
                    $i += 4
                }
                else {
                    [void] $builder.Append('\'); [void] $builder.Append($next)
                }
            }
            default {
                [void] $builder.Append('\'); [void] $builder.Append($next)
            }
        }
    }
    return $builder.ToString()
}

<# Reescapa o texto para o formato `.properties` (onde a barra invertida escapa). #>
function ConvertTo-PropertyValue {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Text)

    $value = $Text.Replace('\', '\\')
    $value = $value.Replace("`r`n", '\n').Replace("`r", '\n').Replace("`n", '\n')
    $value = $value.Replace("`t", '\t')
    if ($value.StartsWith(' ') -or $value.StartsWith("`t")) {
        $value = '\' + $value
    }
    return $value
}

if (-not (Test-Path -LiteralPath $SourceRoot)) {
    throw "Pasta de origem não encontrada: $SourceRoot. Clone o projeto Android ou use -SourceRoot."
}

# `$Destination` já vem resolvido (é absoluto quando ninguém passou -Destination);
# aplicar `Join-Path $PSScriptRoot` de novo montaria um caminho inválido no Windows.
$destinationFull = [System.IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Force -Path $destinationFull | Out-Null

$summary = [ordered]@{}
foreach ($language in $languages) {
    $sourceFile = Join-Path (Join-Path $SourceRoot $language.Folder) 'strings.xml'
    if (-not (Test-Path -LiteralPath $sourceFile)) {
        Write-Warning "Ignorado (nao existe): $sourceFile"
        continue
    }

    [xml] $document = Get-Content -LiteralPath $sourceFile -Raw -Encoding UTF8

    # Cabecalho em ASCII: este texto e escrito DENTRO do arquivo gerado, e o
    # script pode ser lido como ANSI pelo Windows PowerShell 5.1.
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# Gerado por tools/convert-strings.ps1 a partir de $($language.Folder)/strings.xml")
    $lines.Add("# do projeto Android ArkZ ARModelViewer. Nao edite a mao: rode o script de novo.")
    $lines.Add("# Locale: $($language.Locale).")
    $lines.Add('')

    $count = 0
    foreach ($node in $document.resources.string) {
        $name = [string] $node.name
        $decoded = ConvertFrom-AndroidEscape -Text $node.InnerText
        $lines.Add("$name=$(ConvertTo-PropertyValue -Text $decoded)")
        $count++
    }

    $target = Join-Path $destinationFull $language.Bundle
    [System.IO.File]::WriteAllText(
        $target,
        ($lines -join "`n") + "`n",
        (New-Object System.Text.UTF8Encoding($false))
    )

    # O mesmo bundle pode receber duas pastas (values e values-pt-rBR, ambas
    # pt-BR): o resumo guarda a ultima gravacao, que e a que vale no arquivo.
    $summary[$language.Bundle] = [pscustomobject]@{
        Locale = $language.Locale
        Bundle = $language.Bundle
        Chaves = $count
    }
}

Write-Host ''
Write-Host "Bundles gerados em $destinationFull" -ForegroundColor Cyan
$summary.Values | Format-Table -AutoSize
Write-Host 'Próximo passo: powershell -ExecutionPolicy Bypass -File tools/apply-i18n-overrides.ps1' -ForegroundColor DarkGray
