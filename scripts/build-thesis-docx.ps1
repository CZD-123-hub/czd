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
    if ($null -eq $Text) { return "" }
    return [System.Security.SecurityElement]::Escape($Text)
}

function New-RunXml {
    param(
        [string]$Text,
        [int]$FontSize = 24,
        [bool]$Bold = $false,
        [string]$FontEastAsia = "SimSun",
        [string]$FontAscii = "Times New Roman"
    )

    $boldXml = if ($Bold) { "<w:b/><w:bCs/>" } else { "" }
    $escaped = Escape-Xml $Text
    return "<w:r><w:rPr><w:rFonts w:ascii=`"$FontAscii`" w:hAnsi=`"$FontAscii`" w:eastAsia=`"$FontEastAsia`" w:cs=`"$FontAscii`"/><w:sz w:val=`"$FontSize`"/><w:szCs w:val=`"$FontSize`"/>$boldXml</w:rPr><w:t xml:space=`"preserve`">$escaped</w:t></w:r>"
}

function New-ParagraphXml {
    param(
        [string]$Text,
        [int]$FontSize = 24,
        [bool]$Bold = $false,
        [string]$Justify = "both",
        [bool]$FirstLineIndent = $true,
        [string]$FontEastAsia = "SimSun",
        [string]$FontAscii = "Times New Roman",
        [int]$Before = 0,
        [int]$After = 0,
        [int]$Line = 360
    )

    $indentXml = if ($FirstLineIndent) { '<w:ind w:firstLineChars="200" w:firstLine="420"/>' } else { "" }
    $runXml = New-RunXml -Text $Text -FontSize $FontSize -Bold $Bold -FontEastAsia $FontEastAsia -FontAscii $FontAscii
    return "<w:p><w:pPr><w:jc w:val=`"$Justify`"/>$indentXml<w:spacing w:before=`"$Before`" w:after=`"$After`" w:line=`"$Line`" w:lineRule=`"auto`"/></w:pPr>$runXml</w:p>"
}

function New-EmptyParagraphXml {
    return "<w:p/>"
}

function New-PageBreakXml {
    return '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'
}

function New-HeadingXml {
    param(
        [string]$Text,
        [int]$Level
    )

    switch ($Level) {
        1 {
            $runXml = New-RunXml -Text $Text -FontSize 32 -Bold $true -FontEastAsia "SimHei" -FontAscii "Times New Roman"
            return "<w:p><w:pPr><w:jc w:val=`"center`"/><w:spacing w:before=`"240`" w:after=`"240`" w:line=`"360`" w:lineRule=`"auto`"/></w:pPr>$runXml</w:p>"
        }
        2 {
            $runXml = New-RunXml -Text $Text -FontSize 28 -Bold $true -FontEastAsia "SimHei" -FontAscii "Times New Roman"
            return "<w:p><w:pPr><w:jc w:val=`"left`"/><w:spacing w:before=`"180`" w:after=`"120`" w:line=`"360`" w:lineRule=`"auto`"/></w:pPr>$runXml</w:p>"
        }
        default {
            $runXml = New-RunXml -Text $Text -FontSize 26 -Bold $true -FontEastAsia "SimHei" -FontAscii "Times New Roman"
            return "<w:p><w:pPr><w:jc w:val=`"left`"/><w:spacing w:before=`"120`" w:after=`"80`" w:line=`"360`" w:lineRule=`"auto`"/></w:pPr>$runXml</w:p>"
        }
    }
}

function New-ReferenceParagraphXml {
    param([string]$Text)
    $runXml = New-RunXml -Text $Text -FontSize 22 -Bold $false -FontEastAsia "SimSun" -FontAscii "Times New Roman"
    return "<w:p><w:pPr><w:jc w:val=`"left`"/><w:ind w:left=`"540`" w:hanging=`"420`"/><w:spacing w:line=`"360`" w:lineRule=`"auto`"/></w:pPr>$runXml</w:p>"
}

function New-CoverLineXml {
    param(
        [string]$Text,
        [int]$FontSize = 28,
        [bool]$Bold = $false,
        [int]$Before = 0,
        [int]$After = 120
    )
    $runXml = New-RunXml -Text $Text -FontSize $FontSize -Bold $Bold -FontEastAsia "SimSun" -FontAscii "Times New Roman"
    return "<w:p><w:pPr><w:jc w:val=`"center`"/><w:spacing w:before=`"$Before`" w:after=`"$After`" w:line=`"420`" w:lineRule=`"auto`"/></w:pPr>$runXml</w:p>"
}

function New-TableXml {
    param([string[]]$Rows)

    $xmlRows = New-Object System.Collections.Generic.List[string]
    foreach ($row in $Rows) {
        $trimmed = $row.Trim()
        if ($trimmed -match '^\|\s*-') { continue }
        $cells = $trimmed.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
        $cellXml = New-Object System.Collections.Generic.List[string]
        foreach ($cell in $cells) {
            $runXml = New-RunXml -Text $cell -FontSize 21 -FontEastAsia "SimSun" -FontAscii "Times New Roman"
            $cellXml.Add("<w:tc><w:tcPr><w:tcMar><w:top w:w=`"80`" w:type=`"dxa`"/><w:left w:w=`"80`" w:type=`"dxa`"/><w:bottom w:w=`"80`" w:type=`"dxa`"/><w:right w:w=`"80`" w:type=`"dxa`"/></w:tcMar></w:tcPr><w:p><w:pPr><w:jc w:val=`"center`"/></w:pPr>$runXml</w:p></w:tc>")
        }
        $xmlRows.Add("<w:tr>$($cellXml -join '')</w:tr>")
    }

    return @"
<w:tbl>
  <w:tblPr>
    <w:tblW w:w="0" w:type="auto"/>
    <w:tblBorders>
      <w:top w:val="single" w:sz="4" w:space="0" w:color="000000"/>
      <w:left w:val="single" w:sz="4" w:space="0" w:color="000000"/>
      <w:bottom w:val="single" w:sz="4" w:space="0" w:color="000000"/>
      <w:right w:val="single" w:sz="4" w:space="0" w:color="000000"/>
      <w:insideH w:val="single" w:sz="4" w:space="0" w:color="000000"/>
      <w:insideV w:val="single" w:sz="4" w:space="0" w:color="000000"/>
    </w:tblBorders>
    <w:tblLook w:val="04A0"/>
  </w:tblPr>
  $($xmlRows -join "`n  ")
</w:tbl>
"@
}

function Convert-MarkdownToBodyXml {
    param([string[]]$Lines)

    $paragraphs = New-Object System.Collections.Generic.List[string]
    $inTable = $false
    $tableRows = New-Object System.Collections.Generic.List[string]
    $topHeadingCount = 0
    $coverMode = $false
    $chapterChar = [string][char]0x7AE0

    for ($i = 0; $i -lt $Lines.Count; $i++) {
        $line = $Lines[$i].TrimEnd()

        if ($line.Trim().StartsWith("|")) {
            $inTable = $true
            $tableRows.Add($line)
            continue
        }

        if ($inTable) {
            $paragraphs.Add((New-TableXml -Rows $tableRows.ToArray()))
            $paragraphs.Add((New-EmptyParagraphXml))
            $tableRows.Clear()
            $inTable = $false
        }

        if ([string]::IsNullOrWhiteSpace($line)) {
            $paragraphs.Add((New-EmptyParagraphXml))
            continue
        }

        if ($line.Trim() -eq "\newpage") {
            $paragraphs.Add((New-PageBreakXml))
            $coverMode = $false
            continue
        }

        if ($line.StartsWith("# ")) {
            $heading = $line.Substring(2).Trim()
            $topHeadingCount++
            if ($topHeadingCount -eq 1) {
                $coverMode = $true
                $paragraphs.Add((New-CoverLineXml -Text $heading -FontSize 36 -Bold $true -Before 480 -After 900))
            } elseif ($heading.Contains($chapterChar) -and $heading -match '\d+') {
                $paragraphs.Add((New-PageBreakXml))
                $paragraphs.Add((New-HeadingXml -Text $heading -Level 1))
            } else {
                $paragraphs.Add((New-HeadingXml -Text $heading -Level 1))
            }
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

        $trimmedLine = $line.Trim()
        $noIndent = $false
        if ($trimmedLine -match '^\d+\. ' -or $trimmedLine.StartsWith("-") -or $trimmedLine.StartsWith("Keywords:")) {
            $noIndent = $true
        }

        if ($coverMode) {
            $paragraphs.Add((New-CoverLineXml -Text $trimmedLine -FontSize 28 -After 180))
        } else {
            $paragraphs.Add((New-ParagraphXml -Text $line -FirstLineIndent (-not $noIndent)))
        }
    }

    if ($inTable) {
        $paragraphs.Add((New-TableXml -Rows $tableRows.ToArray()))
    }

    return $paragraphs
}

$inputFullPath = (Resolve-Path -LiteralPath $InputPath).Path
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDir = [System.IO.Path]::GetDirectoryName($outputFullPath)
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$lines = Get-Content -LiteralPath $inputFullPath -Encoding UTF8
$paragraphs = Convert-MarkdownToBodyXml -Lines $lines

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
  <dc:title>Intelligent Coding Learning Assistant Thesis Draft</dc:title>
  <dc:creator>Chen Zhengda</dc:creator>
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

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("thesis-docx-" + [guid]::NewGuid().ToString("N"))
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
