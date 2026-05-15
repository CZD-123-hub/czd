param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Escape-Xml {
    param([string]$Text)
    if ($null -eq $Text) {
        return ""
    }
    return [System.Security.SecurityElement]::Escape($Text)
}

function New-RunXml {
    param(
        [string]$Text,
        [int]$FontSize = 24,
        [bool]$Bold = $false,
        [string]$Font = "宋体"
    )

    $escaped = Escape-Xml $Text
    $boldXml = if ($Bold) { "<w:b/>" } else { "" }
    return "<w:r><w:rPr><w:rFonts w:ascii=`"$Font`" w:hAnsi=`"$Font`" w:eastAsia=`"$Font`"/><w:sz w:val=`"$FontSize`"/><w:szCs w:val=`"$FontSize`"/>$boldXml</w:rPr><w:t xml:space=`"preserve`">$escaped</w:t></w:r>"
}

function New-ParagraphXml {
    param(
        [string]$Text,
        [int]$FontSize = 24,
        [bool]$Bold = $false,
        [string]$Justify = "left",
        [bool]$FirstLineIndent = $true,
        [string]$Font = "宋体"
    )

    $indentXml = if ($FirstLineIndent) { '<w:ind w:firstLineChars="200" w:firstLine="420"/>' } else { "" }
    $runXml = New-RunXml -Text $Text -FontSize $FontSize -Bold $Bold -Font $Font
    return "<w:p><w:pPr><w:jc w:val=`"$Justify`"/>$indentXml<w:spacing w:line=`"420`" w:lineRule=`"auto`"/></w:pPr>$runXml</w:p>"
}

function New-ReferenceParagraphXml {
    param(
        [string]$Text,
        [int]$FontSize = 22,
        [string]$Font = "宋体"
    )

    $runXml = New-RunXml -Text $Text -FontSize $FontSize -Bold $false -Font $Font
    return "<w:p><w:pPr><w:jc w:val=`"left`"/><w:ind w:left=`"540`" w:hanging=`"420`"/><w:spacing w:line=`"360`" w:lineRule=`"auto`"/></w:pPr>$runXml</w:p>"
}

function New-HeadingXml {
    param(
        [string]$Text,
        [int]$Level
    )

    switch ($Level) {
        1 {
            $runXml = New-RunXml -Text $Text -FontSize 32 -Bold $true -Font "黑体"
            return "<w:p><w:pPr><w:jc w:val=`"center`"/><w:spacing w:before=`"120`" w:after=`"120`"/></w:pPr>$runXml</w:p>"
        }
        2 {
            $runXml = New-RunXml -Text $Text -FontSize 28 -Bold $true -Font "黑体"
            return "<w:p><w:pPr><w:spacing w:before=`"100`" w:after=`"60`"/></w:pPr>$runXml</w:p>"
        }
        3 {
            $runXml = New-RunXml -Text $Text -FontSize 26 -Bold $true -Font "黑体"
            return "<w:p><w:pPr><w:spacing w:before=`"80`" w:after=`"40`"/></w:pPr>$runXml</w:p>"
        }
    }
}

$inputFullPath = (Resolve-Path -LiteralPath $InputPath).Path
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDir = [System.IO.Path]::GetDirectoryName($outputFullPath)

if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$lines = Get-Content -LiteralPath $inputFullPath -Encoding UTF8
$paragraphs = New-Object System.Collections.Generic.List[string]

foreach ($rawLine in $lines) {
    $line = $rawLine.TrimEnd()
    if ([string]::IsNullOrWhiteSpace($line)) {
        $paragraphs.Add('<w:p/>')
        continue
    }

    if ($line.StartsWith("# ")) {
        $paragraphs.Add((New-HeadingXml -Text $line.Substring(2).Trim() -Level 1))
        continue
    }
    if ($line.StartsWith("## ")) {
        $paragraphs.Add((New-HeadingXml -Text $line.Substring(3).Trim() -Level 2))
        continue
    }
    if ($line.StartsWith("### ")) {
        $paragraphs.Add((New-HeadingXml -Text $line.Substring(4).Trim() -Level 3))
        continue
    }

    if ($line -match '^\[\d+\]') {
        $paragraphs.Add((New-ReferenceParagraphXml -Text $line))
        continue
    }

    $firstLineIndent = $true
    if ($line.StartsWith("[") -or $line.StartsWith("1.") -or $line.StartsWith("2.") -or $line.StartsWith("3.") -or
        $line.StartsWith("4.") -or $line.StartsWith("5.") -or $line.StartsWith("6.") -or $line.StartsWith("7.") -or
        $line.StartsWith("8.") -or $line.StartsWith("9.") -or $line.StartsWith("-")) {
        $firstLineIndent = $false
    }

    $paragraphs.Add((New-ParagraphXml -Text $line -FirstLineIndent $firstLineIndent))
}

$documentXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas"
 xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
 xmlns:v="urn:schemas-microsoft-com:vml"
 xmlns:wp14="http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing"
 xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
 xmlns:w10="urn:schemas-microsoft-com:office:word"
 xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
 xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml"
 xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"
 xmlns:wpi="http://schemas.microsoft.com/office/word/2010/wordprocessingInk"
 xmlns:wne="http://schemas.microsoft.com/office/word/2006/wordml"
 xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
 mc:Ignorable="w14 wp14">
  <w:body>
    $($paragraphs -join "`n    ")
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1800" w:bottom="1440" w:left="1800" w:header="851" w:footer="992" w:gutter="0"/>
      <w:cols w:space="425"/>
      <w:docGrid w:linePitch="312"/>
    </w:sectPr>
  </w:body>
</w:document>
"@

$contentTypesXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>
"@

$relsXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>
"@

$timestamp = [DateTime]::UtcNow.ToString("s") + "Z"
$coreXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
 xmlns:dc="http://purl.org/dc/elements/1.1/"
 xmlns:dcterms="http://purl.org/dc/terms/"
 xmlns:dcmitype="http://purl.org/dc/dcmitype/"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>智能编程学习助手中期检查报告</dc:title>
  <dc:creator>Codex</dc:creator>
  <cp:lastModifiedBy>Codex</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">$timestamp</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$timestamp</dcterms:modified>
</cp:coreProperties>
"@

$appXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
 xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>Microsoft Office Word</Application>
  <DocSecurity>0</DocSecurity>
  <ScaleCrop>false</ScaleCrop>
  <Company>OpenAI</Company>
  <LinksUpToDate>false</LinksUpToDate>
  <SharedDoc>false</SharedDoc>
  <HyperlinksChanged>false</HyperlinksChanged>
  <AppVersion>16.0000</AppVersion>
</Properties>
"@

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("midterm-docx-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempRoot | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tempRoot "_rels") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tempRoot "word") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tempRoot "docProps") | Out-Null

try {
    Set-Content -LiteralPath (Join-Path $tempRoot "[Content_Types].xml") -Value $contentTypesXml -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $tempRoot "_rels\.rels") -Value $relsXml -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $tempRoot "word\document.xml") -Value $documentXml -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $tempRoot "docProps\core.xml") -Value $coreXml -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $tempRoot "docProps\app.xml") -Value $appXml -Encoding UTF8

    if (Test-Path -LiteralPath $outputFullPath) {
        Remove-Item -LiteralPath $outputFullPath -Force
    }

    $zipPath = [System.IO.Path]::ChangeExtension($outputFullPath, ".zip")
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }

    Compress-Archive -Path (Join-Path $tempRoot "*") -DestinationPath $zipPath -Force
    Move-Item -LiteralPath $zipPath -Destination $outputFullPath -Force
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

Write-Output "Generated: $outputFullPath"
