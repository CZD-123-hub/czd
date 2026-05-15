param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [Parameter(Mandatory = $true)]
    [string]$ReferenceMdPath,
    [Parameter(Mandatory = $true)]
    [string]$IndexPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Xml.Linq

$inputFullPath = (Resolve-Path -LiteralPath $InputPath).Path
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$referenceMdFullPath = (Resolve-Path -LiteralPath $ReferenceMdPath).Path
$indexFullPath = (Resolve-Path -LiteralPath $IndexPath).Path
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("docx-citations-" + [guid]::NewGuid().ToString("N"))

$referenceTitle = ([string][char]0x53C2) + ([string][char]0x8003) + ([string][char]0x6587) + ([string][char]0x732E)
$thanksTitle = ([string][char]0x81F4) + ([string][char]0x8C22)

$citations = @{
    191 = "[7][15][21]"
    192 = "[1][6][9][23]"
    193 = "[1][3][4][12][24]"
    197 = "[5][9][10][23]"
    198 = "[14][17][18]"
    199 = "[1][3][4][12][24]"
    200 = "[2][3][14][21]"
    218 = "[3][4][24]"
    232 = "[3][25]"
    234 = "[3][4][12][24][25]"
    235 = "[1][3][12]"
    243 = "[3][4][24]"
    259 = "[2][11][14][15]"
    297 = "[11][15]"
    303 = "[13][14]"
    308 = "[7][17][18]"
    310 = "[5][22][23]"
    657 = "[3][4][12][24]"
    658 = "[3][24][25]"
    665 = "[2][16]"
    685 = "[16][19][20]"
    692 = "[3][24][25]"
    709 = "[16][20]"
    718 = "[11][15]"
    735 = "[13][14]"
    744 = "[7][17][18]"
    752 = "[22][23]"
    785 = "[3][5][9][24]"
    817 = "[3][4][24][25]"
    869 = "[3][4][24][25]"
    871 = "[1][12][16][19]"
    899 = "[11][15]"
    930 = "[7][17][18]"
    953 = "[7][17][18]"
    955 = "[13][14]"
    1012 = "[3][24][25]"
    1014 = "[7][17][18]"
    1042 = "[7][17][18]"
    1044 = "[5][22][23]"
    1071 = "[5][22][23]"
    1219 = "[3]"
    1222 = "[7][17]"
    1242 = "[2][3][24]"
    1245 = "[3][21][23]"
    1254 = "[3][24][25]"
    1257 = "[1][12][16]"
    1258 = "[11][15]"
    1260 = "[7][17][18]"
    1261 = "[22][23]"
    1262 = "[7][17]"
}

function Get-ReferenceLines {
    param([string]$Path)

    $content = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($Path))
    $lines = $content -split "`r?`n"
    $result = New-Object System.Collections.Generic.List[string]
    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if ($trimmed -match '^\[\d+\]\s+') {
            $result.Add($trimmed)
        }
        if ($result.Count -ge 25) {
            break
        }
    }
    if ($result.Count -lt 25) {
        throw "Expected 25 reference entries, found $($result.Count)."
    }
    return $result.ToArray()
}

function Get-IndexTextMap {
    param([string]$Path)

    $content = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($Path))
    $lines = $content -split "`r?`n"
    $map = @{}
    foreach ($line in $lines) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $parts = $line -split "``t", 3
        if ($parts.Count -lt 3) {
            $parts = $line -split "`t", 3
        }
        if ($parts.Count -lt 3) {
            continue
        }
        $number = 0
        if ([int]::TryParse($parts[0], [ref]$number)) {
            $map[$number] = $parts[2].Trim()
        }
    }
    return $map
}

function Normalize-Text {
    param([string]$Text)
    if ($null -eq $Text) {
        return ""
    }
    return ($Text -replace '\s+', '')
}

function Get-ParagraphText {
    param([System.Xml.Linq.XElement]$Paragraph, [System.Xml.Linq.XNamespace]$W)
    return (($Paragraph.Descendants($W + "t") | ForEach-Object { $_.Value }) -join "")
}

function New-TextRun {
    param(
        [string]$Text,
        [System.Xml.Linq.XElement]$RunPr,
        [System.Xml.Linq.XNamespace]$W
    )

    $run = [System.Xml.Linq.XElement]::new($W + "r")
    if ($null -ne $RunPr) {
        $run.Add([System.Xml.Linq.XElement]::new($RunPr))
    }
    $textElement = [System.Xml.Linq.XElement]::new($W + "t")
    $textElement.SetAttributeValue([System.Xml.Linq.XNamespace]::Xml + "space", "preserve")
    $textElement.Value = $Text
    $run.Add($textElement)
    return $run
}

function New-ReferenceParagraph {
    param(
        [string]$Text,
        [System.Xml.Linq.XElement]$ParagraphPr,
        [System.Xml.Linq.XElement]$RunPr,
        [System.Xml.Linq.XNamespace]$W
    )

    $paragraph = [System.Xml.Linq.XElement]::new($W + "p")
    if ($null -ne $ParagraphPr) {
        $paragraph.Add([System.Xml.Linq.XElement]::new($ParagraphPr))
    }
    $paragraph.Add((New-TextRun -Text $Text -RunPr $RunPr -W $W))
    return $paragraph
}

try {
    $references = Get-ReferenceLines -Path $referenceMdFullPath
    $indexTextMap = Get-IndexTextMap -Path $indexFullPath
    [System.IO.Compression.ZipFile]::ExtractToDirectory($inputFullPath, $tempRoot)
    $documentPath = Join-Path $tempRoot "word\document.xml"
    $doc = [System.Xml.Linq.XDocument]::Load($documentPath)
    $w = [System.Xml.Linq.XNamespace]"http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    $body = $doc.Root.Element($w + "body")
    $paragraphs = @($doc.Descendants($w + "p"))

    foreach ($entry in $citations.GetEnumerator()) {
        $indexNumber = [int]$entry.Key
        if (-not $indexTextMap.ContainsKey($indexNumber)) {
            throw "Index file does not contain paragraph $indexNumber."
        }

        $targetText = [string]$indexTextMap[$indexNumber]
        $normalizedTarget = Normalize-Text -Text $targetText
        if ($normalizedTarget.Length -lt 8) {
            throw "Paragraph $indexNumber target text is too short for safe matching."
        }

        $paragraph = $null
        foreach ($candidate in $paragraphs) {
            $candidateText = Get-ParagraphText -Paragraph $candidate -W $w
            $normalizedCandidate = Normalize-Text -Text $candidateText
            if ($normalizedCandidate -eq $normalizedTarget) {
                $paragraph = $candidate
                break
            }
        }
        if ($null -eq $paragraph) {
            foreach ($candidate in $paragraphs) {
                $candidateText = Get-ParagraphText -Paragraph $candidate -W $w
                $normalizedCandidate = Normalize-Text -Text $candidateText
                if ($normalizedCandidate.Contains($normalizedTarget) -or $normalizedTarget.Contains($normalizedCandidate)) {
                    $paragraph = $candidate
                    break
                }
            }
        }
        if ($null -eq $paragraph) {
            throw "Could not match indexed paragraph $indexNumber in docx."
        }

        $currentText = Get-ParagraphText -Paragraph $paragraph -W $w
        $citationText = [string]$entry.Value
        if ($currentText.Contains($citationText)) {
            continue
        }
        $lastRun = $paragraph.Elements($w + "r") | Select-Object -Last 1
        $lastRunPr = if ($null -ne $lastRun) { $lastRun.Element($w + "rPr") } else { $null }
        $paragraph.Add((New-TextRun -Text $citationText -RunPr $lastRunPr -W $w))
    }

    $paragraphs = @($doc.Descendants($w + "p"))
    $referenceTitleIndex = -1
    for ($i = 0; $i -lt $paragraphs.Count; $i++) {
        if ((Get-ParagraphText -Paragraph $paragraphs[$i] -W $w).Trim() -eq $referenceTitle) {
            $referenceTitleIndex = $i
            break
        }
    }
    if ($referenceTitleIndex -lt 0) {
        throw "Could not find reference title paragraph."
    }

    $thanksIndex = -1
    for ($i = $referenceTitleIndex + 1; $i -lt $paragraphs.Count; $i++) {
        if ((Get-ParagraphText -Paragraph $paragraphs[$i] -W $w).Trim() -eq $thanksTitle) {
            $thanksIndex = $i
            break
        }
    }
    if ($thanksIndex -lt 0) {
        throw "Could not find acknowledgements title paragraph after references."
    }

    $sampleRefParagraph = $paragraphs[$referenceTitleIndex + 1]
    $sampleRefPr = $sampleRefParagraph.Element($w + "pPr")
    $sampleRun = $sampleRefParagraph.Elements($w + "r") | Select-Object -First 1
    $sampleRunPr = if ($null -ne $sampleRun) { $sampleRun.Element($w + "rPr") } else { $null }

    for ($i = $thanksIndex - 1; $i -gt $referenceTitleIndex; $i--) {
        $paragraphs[$i].Remove()
    }

    $paragraphs = @($doc.Descendants($w + "p"))
    $thanksParagraph = $paragraphs | Where-Object { (Get-ParagraphText -Paragraph $_ -W $w).Trim() -eq $thanksTitle } | Select-Object -First 1
    foreach ($reference in $references) {
        $thanksParagraph.AddBeforeSelf((New-ReferenceParagraph -Text $reference -ParagraphPr $sampleRefPr -RunPr $sampleRunPr -W $w))
    }

    $doc.Save($documentPath)

    $outputDir = [System.IO.Path]::GetDirectoryName($outputFullPath)
    if (-not (Test-Path -LiteralPath $outputDir)) {
        New-Item -ItemType Directory -Path $outputDir | Out-Null
    }
    if (Test-Path -LiteralPath $outputFullPath) {
        Remove-Item -LiteralPath $outputFullPath -Force
    }
    [System.IO.Compression.ZipFile]::CreateFromDirectory($tempRoot, $outputFullPath)
    Write-Output "Generated: $outputFullPath"
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
